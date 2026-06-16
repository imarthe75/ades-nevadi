package mx.ades.modules.catalogos;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogsQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> roles() {
        return jdbc.queryForList(
                "SELECT id, nombre_rol, descripcion, nivel_acceso FROM ades_roles " +
                "WHERE is_active = TRUE ORDER BY nivel_acceso, nombre_rol"
        );
    }

    public List<Map<String, Object>> periodos(UUID cicloId, UUID grupoId) {
        StringBuilder sql = new StringBuilder(
                "SELECT pe.id, pe.nombre_periodo, pe.numero_periodo, pe.tipo_periodo, " +
                "pe.ciclo_escolar_id, pe.fecha_inicio, pe.fecha_fin, pe.fecha_entrega_boletas " +
                "FROM ades_periodos_evaluacion pe WHERE pe.is_active = TRUE");
        List<Object> params = new ArrayList<>();
        if (cicloId != null) {
            sql.append(" AND pe.ciclo_escolar_id = ?");
            params.add(cicloId);
        } else if (grupoId != null) {
            sql.append(" AND pe.ciclo_escolar_id = (SELECT ciclo_escolar_id FROM ades_grupos WHERE id = ?)");
            params.add(grupoId);
        }
        sql.append(" ORDER BY pe.numero_periodo");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> paises() {
        return jdbc.queryForList(
                "SELECT id, clave_pais, nombre_pais, nacionalidad FROM ades_paises " +
                "WHERE is_active = TRUE ORDER BY nombre_pais"
        );
    }

    public List<Map<String, Object>> nacionalidades() {
        return jdbc.queryForList(
                "SELECT DISTINCT nacionalidad FROM ades_paises " +
                "WHERE is_active = TRUE AND nacionalidad IS NOT NULL " +
                "ORDER BY nacionalidad"
        );
    }

    public List<Map<String, Object>> lenguasIndigenas(String familia) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, familia_linguistica, agrupacion, autonym " +
                "FROM ades_lenguas_indigenas WHERE activa = TRUE ");
        List<Object> params = new ArrayList<>();
        if (familia != null && !familia.isBlank()) {
            sql.append("AND familia_linguistica = ? ");
            params.add(familia);
        }
        sql.append("ORDER BY agrupacion");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> familiasLinguisticas() {
        return jdbc.queryForList(
                "SELECT DISTINCT familia_linguistica, COUNT(*) AS total " +
                "FROM ades_lenguas_indigenas WHERE activa = TRUE " +
                "GROUP BY familia_linguistica ORDER BY familia_linguistica"
        );
    }

    public List<Map<String, Object>> nivelesIngles() {
        return jdbc.queryForList(
                "SELECT id, nivel, nombre, descripcion, equivalencia_cambridge, " +
                "rango_toefl_ibt, rango_ielts " +
                "FROM ades_niveles_ingles WHERE activo = TRUE ORDER BY orden"
        );
    }
}
