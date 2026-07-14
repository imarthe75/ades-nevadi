package mx.ades.modules.stats.query;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;

@Service
public class StatsQueryService {

    private final NamedParameterJdbcTemplate namedJdbc;
    private final StringRedisTemplate redis;

    public StatsQueryService(NamedParameterJdbcTemplate namedJdbc,
                             StringRedisTemplate redis) {
        this.namedJdbc = namedJdbc;
        this.redis = redis;
    }

    public Map<String, Object> resumen(UUID plantelId) {
        // Cast explícito a text → uuid para que PgBouncer (transaction mode) infiera el tipo.
        // Sin el cast, $1 IS NULL falla con "could not determine data type of parameter $1".
        String sql = """
            SELECT
              (SELECT COUNT(*) FROM ades_estudiantes
               WHERE is_active = true
                 AND (:plantelId::text IS NULL OR plantel_id = :plantelId::uuid))        AS total_alumnos,
              (SELECT COUNT(*) FROM ades_profesores
               WHERE is_active = true
                 AND (:plantelId::text IS NULL OR plantel_id = :plantelId::uuid))        AS total_profesores,
              (SELECT COUNT(*) FROM ades_grupos g
               JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id
               JOIN ades_grados gr          ON gr.id = g.grado_id
               WHERE g.is_active = true AND c.es_vigente = true
                 AND (:plantelId::text IS NULL OR gr.plantel_id = :plantelId::uuid))     AS total_grupos_activos,
              (SELECT COUNT(*) FROM ades_clases
               WHERE fecha_clase = CURRENT_DATE)                             AS total_clases_hoy
            """;

        var params = new MapSqlParameterSource();
        params.addValue("plantelId", plantelId != null ? plantelId.toString() : null, java.sql.Types.VARCHAR);
        return namedJdbc.queryForMap(sql, params);
    }

    public List<Map<String, Object>> distribucion(UUID plantelId) {
        String sql = """
            SELECT
                n.nombre_nivel,
                COUNT(DISTINCT i.estudiante_id) AS total_alumnos,
                COUNT(DISTINCT g.id)             AS total_grupos
            FROM ades_niveles_educativos n
            LEFT JOIN ades_grados gr         ON gr.nivel_educativo_id = n.id
                                             AND (:plantelId::text IS NULL OR gr.plantel_id = :plantelId::uuid)
            LEFT JOIN ades_grupos g           ON g.grado_id = gr.id AND g.is_active = true
            LEFT JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id AND c.es_vigente = true
            LEFT JOIN ades_inscripciones i    ON i.grupo_id = g.id AND i.is_active = true
            WHERE n.is_active = true
            GROUP BY n.nombre_nivel
            ORDER BY n.nombre_nivel
            """;

        var params2 = new MapSqlParameterSource();
        params2.addValue("plantelId", plantelId != null ? plantelId.toString() : null, java.sql.Types.VARCHAR);
        return namedJdbc.queryForList(sql, params2);
    }

    public long databaseSizeBytes() {
        Long size = namedJdbc.queryForObject(
                "SELECT pg_database_size(current_database())",
                new MapSqlParameterSource(), Long.class);
        return size != null ? size : 0L;
    }

    // ── AD-030: Telemetría completa del servidor ─────────────────────────────

    public Map<String, Object> telemetria() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("base_de_datos", _dbStats());
        result.put("conexiones", _connectionStats());
        result.put("tablas_grandes", _topTables());
        result.put("particiones", _partitionStats());
        result.put("sistema", _systemStats());
        result.put("colas_celery", _celeryQueueStats());
        result.put("actualizacion", new java.util.Date().toInstant().toString());

        return result;
    }

    private Map<String, Object> _dbStats() {
        String sql = """
            SELECT
                pg_database_size(current_database())                  AS db_size_bytes,
                (SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name LIKE 'ades_%')                      AS total_tablas,
                (SELECT COUNT(*) FROM pg_matviews)                    AS total_mv,
                (SELECT COUNT(*) FROM pg_indexes
                 WHERE schemaname = 'public')                         AS total_indices,
                (SELECT SUM(n_live_tup) FROM pg_stat_user_tables
                 WHERE schemaname = 'public')                         AS total_filas_estimadas
            """;
        try {
            Map<String, Object> row = namedJdbc.queryForMap(sql, new MapSqlParameterSource());
            long sizeBytes = row.get("db_size_bytes") instanceof Number n ? n.longValue() : 0L;
            row.put("db_size_mb", Math.round(sizeBytes / 1_048_576.0 * 100) / 100.0);
            row.put("db_size_gb", Math.round(sizeBytes / 1_073_741_824.0 * 1000) / 1000.0);
            return row;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> _connectionStats() {
        String sql = """
            SELECT
                COUNT(*)                                          AS total_conexiones,
                COUNT(*) FILTER (WHERE state = 'active')         AS activas,
                COUNT(*) FILTER (WHERE state = 'idle')           AS inactivas,
                COUNT(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction,
                (SELECT setting::int FROM pg_settings
                 WHERE name = 'max_connections')                  AS max_conexiones
            FROM pg_stat_activity
            WHERE datname = current_database()
            """;
        try {
            Map<String, Object> row = namedJdbc.queryForMap(sql, new MapSqlParameterSource());
            Object total = row.get("total_conexiones");
            Object max   = row.get("max_conexiones");
            if (total instanceof Number t && max instanceof Number m && m.intValue() > 0) {
                row.put("pct_uso", Math.round(t.doubleValue() / m.doubleValue() * 1000) / 10.0);
            }
            return row;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private List<Map<String, Object>> _topTables() {
        String sql = """
            SELECT
                relname                                         AS tabla,
                pg_total_relation_size(oid)                     AS size_bytes,
                pg_size_pretty(pg_total_relation_size(oid))     AS size_legible,
                n_live_tup                                      AS filas_estimadas
            FROM pg_stat_user_tables
            WHERE schemaname = 'public'
            ORDER BY pg_total_relation_size(oid) DESC
            LIMIT 10
            """;
        try {
            return namedJdbc.queryForList(sql, new MapSqlParameterSource());
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> _partitionStats() {
        String sql = """
            SELECT
                COUNT(*)                                     AS total_particiones,
                COUNT(DISTINCT inhparent)                    AS tablas_particionadas,
                SUM(pg_total_relation_size(inhrelid))        AS size_total_bytes,
                pg_size_pretty(SUM(pg_total_relation_size(inhrelid))) AS size_legible
            FROM pg_inherits
            JOIN pg_class ON pg_class.oid = inhrelid
            WHERE pg_class.relkind = 'r'
            """;
        try {
            Map<String, Object> row = namedJdbc.queryForMap(sql, new MapSqlParameterSource());
            Object sizeB = row.get("size_total_bytes");
            if (sizeB instanceof Number n) {
                row.put("size_total_mb", Math.round(n.longValue() / 1_048_576.0 * 100) / 100.0);
            }
            return row;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> _systemStats() {
        Map<String, Object> sys = new LinkedHashMap<>();

        // JVM memory
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long heapUsed  = mem.getHeapMemoryUsage().getUsed();
        long heapMax   = mem.getHeapMemoryUsage().getMax();
        sys.put("jvm_heap_used_mb",  Math.round(heapUsed  / 1_048_576.0 * 10) / 10.0);
        sys.put("jvm_heap_max_mb",   Math.round(heapMax   / 1_048_576.0 * 10) / 10.0);
        sys.put("jvm_heap_pct",      heapMax > 0 ? Math.round(heapUsed * 1000.0 / heapMax) / 10.0 : -1);

        // CPU disponible
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        sys.put("cpu_disponibles",   os.getAvailableProcessors());
        sys.put("carga_sistema",     Math.round(os.getSystemLoadAverage() * 100) / 100.0);

        // Disco — directorio de trabajo
        try {
            File disco = new File("/");
            sys.put("disco_total_gb",  Math.round(disco.getTotalSpace()  / 1_073_741_824.0 * 100) / 100.0);
            sys.put("disco_libre_gb",  Math.round(disco.getFreeSpace()   / 1_073_741_824.0 * 100) / 100.0);
            sys.put("disco_usado_gb",  Math.round((disco.getTotalSpace() - disco.getFreeSpace()) / 1_073_741_824.0 * 100) / 100.0);
            long total = disco.getTotalSpace();
            long usado = total - disco.getFreeSpace();
            sys.put("disco_pct",       total > 0 ? Math.round(usado * 1000.0 / total) / 10.0 : -1);
        } catch (Exception e) {
            sys.put("disco_error", e.getMessage());
        }

        // Threads JVM
        sys.put("jvm_threads_activos", ManagementFactory.getThreadMXBean().getThreadCount());
        sys.put("jvm_uptime_min",
                ManagementFactory.getRuntimeMXBean().getUptime() / 60_000);

        return sys;
    }

    private Map<String, Object> _celeryQueueStats() {
        Map<String, Object> colas = new LinkedHashMap<>();
        String[] queues = {"celery", "ades:high", "ades:low"};
        for (String q : queues) {
            try {
                Long size = redis.opsForList().size(q);
                colas.put(q.replace(":", "_"), size != null ? size : 0L);
            } catch (Exception e) {
                colas.put(q.replace(":", "_"), -1);
            }
        }
        return colas;
    }

    public Map<String, Object> directorKPIs(UUID plantelId) {
        String kpisSql = """
            SELECT
                COALESCE(SUM(total_alumnos), 0) AS total_alumnos,
                COALESCE(SUM(total_grupos), 0) AS total_grupos,
                COALESCE(ROUND(AVG(promedio_institucional), 2), 0) AS promedio_general,
                COALESCE(ROUND(AVG(pct_riesgo_alto), 2), 0) AS pct_riesgo_alto
            FROM ades_bi.mv_resumen_plantel
            WHERE :plantelId::text IS NULL OR plantel_id = :plantelId::uuid
            """;

        // ades_bi.mv_asistencia_mensual quedó obsoleta desde la migración 066
        // (columnas a.presente/a.fecha/g.plantel_id ya no existen) y nunca se
        // volvió a crear — se reemplaza por v_asistencias_resumen (vigente,
        // creada en 074b/077), agregada aquí por plantel vía ades_estudiantes.
        String asistenciaSql = """
            SELECT
                COALESCE(ROUND(100.0 * SUM(CASE WHEN v.estatus_asistencia = 'PRESENTE' THEN v.total ELSE 0 END)
                    / NULLIF(SUM(v.total), 0), 2), 0) AS pct_asistencia
            FROM v_asistencias_resumen v
            JOIN ades_estudiantes e ON e.id = v.estudiante_id
            WHERE :plantelId::text IS NULL OR e.plantel_id = :plantelId::uuid
            """;

        String coberturaSql = """
            SELECT
                COALESCE(ROUND(AVG(pct_cobertura), 2), 0) AS pct_cobertura
            FROM ades_bi.mv_cobertura_curricular cc
            WHERE :plantelId::text IS NULL OR cc.nombre_plantel IN (
                SELECT nombre_plantel FROM ades_planteles WHERE id = :plantelId::uuid
            )
            """;

        var params = new MapSqlParameterSource();
        params.addValue("plantelId", plantelId != null ? plantelId.toString() : null, java.sql.Types.VARCHAR);

        try {
            Map<String, Object> kpis = namedJdbc.queryForMap(kpisSql, params);
            Map<String, Object> attendance = namedJdbc.queryForMap(asistenciaSql, params);
            Map<String, Object> coverage = namedJdbc.queryForMap(coberturaSql, params);

            Map<String, Object> result = new HashMap<>(kpis);
            result.putAll(attendance);
            result.putAll(coverage);
            return result;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public List<Map<String, Object>> directorAvanceGrado(UUID plantelId) {
        String sql = """
            SELECT
                nombre_grado AS grado,
                ROUND(AVG(promedio)::numeric, 2) AS promedio_grado,
                ROUND(AVG(aprobados * 100.0 / NULLIF(alumnos_evaluados, 0))::numeric, 2) AS pct_aprobacion
            FROM ades_bi.mv_calificaciones_grupo cg
            WHERE :plantelId::text IS NULL OR cg.nombre_plantel IN (
                SELECT nombre_plantel FROM ades_planteles WHERE id = :plantelId::uuid
            )
            GROUP BY nombre_grado
            ORDER BY nombre_grado
            """;
        var params = new MapSqlParameterSource();
        params.addValue("plantelId", plantelId != null ? plantelId.toString() : null, java.sql.Types.VARCHAR);
        try {
            return namedJdbc.queryForList(sql, params);
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    public List<Map<String, Object>> directorAvanceAsignatura(UUID plantelId) {
        String sql = """
            SELECT
                nombre_materia AS asignatura,
                ROUND(AVG(promedio)::numeric, 2) AS promedio_asignatura,
                ROUND(AVG(aprobados * 100.0 / NULLIF(alumnos_evaluados, 0))::numeric, 2) AS pct_aprobacion
            FROM ades_bi.mv_calificaciones_grupo cg
            WHERE :plantelId::text IS NULL OR cg.nombre_plantel IN (
                SELECT nombre_plantel FROM ades_planteles WHERE id = :plantelId::uuid
            )
            GROUP BY nombre_materia
            ORDER BY promedio_asignatura DESC
            LIMIT 15
            """;
        var params = new MapSqlParameterSource();
        params.addValue("plantelId", plantelId != null ? plantelId.toString() : null, java.sql.Types.VARCHAR);
        try {
            return namedJdbc.queryForList(sql, params);
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }
}
