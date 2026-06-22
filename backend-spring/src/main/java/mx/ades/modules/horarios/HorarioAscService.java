package mx.ades.modules.horarios;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Integración con aSc TimeTables (https://www.asctimetables.com).
 *
 * Genera y consume el XML de import/export de aSc. Cada renglón de
 * {@code ades_horarios} se modela como un {@code <lesson>} + {@code <card>}:
 *   - lesson  → qué se imparte (class + subject + teacher + classroom)
 *   - card    → cuándo (day + period)
 *
 * Los atributos {@code id} usan el UUID real de la BD, lo que garantiza un
 * round-trip exacto: aSc preserva los ids al exportar su solución y al
 * reimportar resolvemos cada entidad directamente por UUID.
 *
 * Convención de días: dia_semana 1=Lunes … 5=Viernes (semana de 5 días).
 * En aSc el día se codifica como bit-string ("10000"=Lunes … "00001"=Viernes).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HorarioAscService {

    private static final int NUM_DIAS = 5;
    private static final String[] DIAS_NOMBRE = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
    private static final String[] DIAS_SHORT  = {"Lu", "Ma", "Mi", "Ju", "Vi"};

    private final JdbcTemplate jdbc;
    private final CrearHorarioUseCase crearHorarioUseCase;

    private static final String EXPORT_SQL =
        "SELECT h.id, h.grupo_id, h.materia_id, h.profesor_id, h.aula_id, h.dia_semana, " +
        "       to_char(h.hora_inicio,'HH24:MI') AS hi, to_char(h.hora_fin,'HH24:MI') AS hf, " +
        "       m.nombre_materia, m.clave_materia, " +
        "       p2.nombre || ' ' || p2.apellido_paterno AS prof_nombre, pr.numero_empleado, " +
        "       a.nombre_aula, a.clave_aula, " +
        "       g.nombre_grupo, gr.nombre_grado, gr.plantel_id " +
        "FROM ades_horarios h " +
        "JOIN ades_grupos g ON g.id = h.grupo_id " +
        "JOIN ades_grados gr ON gr.id = g.grado_id " +
        "JOIN ades_materias m ON m.id = h.materia_id " +
        "JOIN ades_profesores pr ON pr.id = h.profesor_id " +
        "JOIN ades_personas p2 ON p2.id = pr.persona_id " +
        "LEFT JOIN ades_aulas a ON a.id = h.aula_id " +
        "WHERE h.is_active = TRUE AND h.ciclo_escolar_id = ? ";

    // ──────────────────────────────────────────────────────────────────────────
    // EXPORT
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String exportarXml(UUID cicloId, UUID plantelId) {
        String sql = EXPORT_SQL + (plantelId != null ? "AND gr.plantel_id = ? " : "");
        sql += "ORDER BY h.dia_semana, hi";
        List<Map<String, Object>> rows = (plantelId != null)
                ? jdbc.queryForList(sql, cicloId, plantelId)
                : jdbc.queryForList(sql, cicloId);

        // Catálogos únicos (id → [name, short])
        Map<String, String[]> subjects   = new LinkedHashMap<>();
        Map<String, String[]> teachers   = new LinkedHashMap<>();
        Map<String, String[]> classes    = new LinkedHashMap<>();
        Map<String, String[]> classrooms = new LinkedHashMap<>();
        // periodos: "hi-hf" → número (1..N), ordenados por hora de inicio
        TreeMap<String, String[]> slots = new TreeMap<>();   // hi → [hi, hf]
        for (Map<String, Object> r : rows) {
            slots.putIfAbsent(str(r.get("hi")), new String[]{str(r.get("hi")), str(r.get("hf"))});
        }
        Map<String, Integer> periodPorHi = new HashMap<>();
        int pnum = 0;
        List<String[]> periodos = new ArrayList<>();
        for (var e : slots.entrySet()) {
            pnum++;
            periodPorHi.put(e.getKey(), pnum);
            periodos.add(new String[]{String.valueOf(pnum), e.getValue()[0], e.getValue()[1]});
        }

        StringBuilder lessons = new StringBuilder();
        StringBuilder cards = new StringBuilder();
        for (Map<String, Object> r : rows) {
            String hid     = str(r.get("id"));
            String grupoId = str(r.get("grupo_id"));
            String matId   = str(r.get("materia_id"));
            String profId  = str(r.get("profesor_id"));
            String aulaId  = str(r.get("aula_id"));
            int dia        = ((Number) r.get("dia_semana")).intValue();

            subjects.putIfAbsent(matId, new String[]{str(r.get("nombre_materia")), shortOf(r.get("clave_materia"), r.get("nombre_materia"))});
            teachers.putIfAbsent(profId, new String[]{str(r.get("prof_nombre")), shortOf(r.get("numero_empleado"), r.get("prof_nombre"))});
            classes.putIfAbsent(grupoId, new String[]{(str(r.get("nombre_grado")) + " " + str(r.get("nombre_grupo"))).trim(), str(r.get("nombre_grupo"))});
            if (aulaId != null && !aulaId.isBlank()) {
                classrooms.putIfAbsent(aulaId, new String[]{str(r.get("nombre_aula")), shortOf(r.get("clave_aula"), r.get("nombre_aula"))});
            }

            String days = diaToBits(dia);
            int period = periodPorHi.getOrDefault(str(r.get("hi")), 1);
            String roomAttr = (aulaId != null && !aulaId.isBlank()) ? " classroomids=\"" + xml(aulaId) + "\"" : "";

            lessons.append("    <lesson id=\"").append(xml(hid)).append("\"")
                   .append(" classids=\"").append(xml(grupoId)).append("\"")
                   .append(" subjectid=\"").append(xml(matId)).append("\"")
                   .append(" teacherids=\"").append(xml(profId)).append("\"")
                   .append(roomAttr)
                   .append(" periodspercard=\"1\" durationperiods=\"1\" count=\"1\"")
                   .append(" days=\"").append(days).append("\"/>\n");

            cards.append("    <card lessonid=\"").append(xml(hid)).append("\"")
                 .append(" period=\"").append(period).append("\"")
                 .append(" days=\"").append(days).append("\"")
                 .append(roomAttr).append("/>\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!-- aSc TimeTables — exportado por ADES (Instituto Nevadi). IDs = UUID de la BD para round-trip exacto. -->\n");
        sb.append("<timetable>\n");

        // periods
        sb.append("  <periods options=\"canadd,canremove,canupdate\" columns=\"period,name,short,starttime,endtime\">\n");
        if (periodos.isEmpty()) {
            sb.append("    <period period=\"1\" name=\"1\" short=\"1\" starttime=\"08:00\" endtime=\"08:50\"/>\n");
        }
        for (String[] p : periodos) {
            sb.append("    <period period=\"").append(p[0]).append("\" name=\"").append(p[0])
              .append("\" short=\"").append(p[0]).append("\" starttime=\"").append(xml(p[1]))
              .append("\" endtime=\"").append(xml(p[2])).append("\"/>\n");
        }
        sb.append("  </periods>\n");

        // daysdefs (5 días)
        sb.append("  <daysdefs options=\"canadd,canremove,canupdate\" columns=\"name,short,days\">\n");
        for (int i = 0; i < NUM_DIAS; i++) {
            sb.append("    <daysdef name=\"").append(DIAS_NOMBRE[i]).append("\" short=\"").append(DIAS_SHORT[i])
              .append("\" days=\"").append(diaToBits(i + 1)).append("\"/>\n");
        }
        sb.append("  </daysdefs>\n");

        appendCatalog(sb, "subjects", "subject", subjects);
        appendCatalog(sb, "teachers", "teacher", teachers);
        appendCatalog(sb, "classes", "class", classes);
        appendCatalog(sb, "classrooms", "classroom", classrooms);

        sb.append("  <lessons options=\"canadd,canremove,canupdate\" columns=\"id,classids,subjectid,teacherids,classroomids,periodspercard,durationperiods,count,days\">\n");
        sb.append(lessons);
        sb.append("  </lessons>\n");

        sb.append("  <cards options=\"canadd,canremove,canupdate\" columns=\"lessonid,period,days,classroomids\">\n");
        sb.append(cards);
        sb.append("  </cards>\n");

        sb.append("</timetable>\n");
        return sb.toString();
    }

    private void appendCatalog(StringBuilder sb, String wrapper, String tag, Map<String, String[]> items) {
        sb.append("  <").append(wrapper).append(" options=\"canadd,canremove,canupdate\" columns=\"id,name,short\">\n");
        for (var e : items.entrySet()) {
            sb.append("    <").append(tag).append(" id=\"").append(xml(e.getKey()))
              .append("\" name=\"").append(xml(e.getValue()[0]))
              .append("\" short=\"").append(xml(e.getValue()[1])).append("\"/>\n");
        }
        sb.append("  </").append(wrapper).append(">\n");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IMPORT
    // ──────────────────────────────────────────────────────────────────────────

    public record ImportResult(int total, int exitosos, int errores, int eliminados,
                               List<String> detalleErrores) {}

    /**
     * No es {@code @Transactional} a propósito: cada card se inserta con autocommit
     * independiente (igual que los demás importadores) para que un renglón inválido
     * no aborte toda la transacción (PostgreSQL 25P02). Reporta éxitos/errores por card.
     */
    public ImportResult importarXml(byte[] contenido, UUID cicloId, UUID plantelId,
                                    boolean reemplazar, String usuario) {
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Endurecimiento contra XXE (OWASP / NIST SI-10)
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new ByteArrayInputStream(contenido));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML aSc no válido: " + e.getMessage());
        }
        doc.getDocumentElement().normalize();

        // periodos: número → [hi, hf]
        Map<String, String[]> periodos = new HashMap<>();
        NodeList pNodes = doc.getElementsByTagName("period");
        for (int i = 0; i < pNodes.getLength(); i++) {
            Element p = (Element) pNodes.item(i);
            String num = firstNonBlank(p.getAttribute("period"), p.getAttribute("name"), p.getAttribute("short"));
            String hi = normalizaHora(p.getAttribute("starttime"));
            String hf = normalizaHora(p.getAttribute("endtime"));
            if (!num.isBlank() && !hi.isBlank()) periodos.put(num, new String[]{hi, hf});
        }

        // lessons: id → {classid, subjectid, teacherid, classroomid, days}
        Map<String, String[]> lessons = new HashMap<>();
        NodeList lNodes = doc.getElementsByTagName("lesson");
        for (int i = 0; i < lNodes.getLength(); i++) {
            Element l = (Element) lNodes.item(i);
            lessons.put(l.getAttribute("id"), new String[]{
                    firstId(l.getAttribute("classids")),
                    l.getAttribute("subjectid"),
                    firstId(l.getAttribute("teacherids")),
                    firstId(l.getAttribute("classroomids")),
                    firstNonBlank(l.getAttribute("days"), l.getAttribute("day")),
            });
        }

        // Modo reemplazar: desactiva los horarios vigentes del ciclo (+plantel) antes de insertar
        int eliminados = 0;
        if (reemplazar) {
            String delSql = "SELECT h.id FROM ades_horarios h " +
                    "JOIN ades_grupos g ON g.id = h.grupo_id " +
                    "JOIN ades_grados gr ON gr.id = g.grado_id " +
                    "WHERE h.is_active = TRUE AND h.ciclo_escolar_id = ? " +
                    (plantelId != null ? "AND gr.plantel_id = ? " : "");
            List<UUID> ids = (plantelId != null)
                    ? jdbc.queryForList(delSql, UUID.class, cicloId, plantelId)
                    : jdbc.queryForList(delSql, UUID.class, cicloId);
            for (UUID id : ids) {
                jdbc.update("UPDATE ades_horarios SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?", usuario, id);
                eliminados++;
            }
        }

        List<String> errores = new ArrayList<>();
        int exitosos = 0;
        NodeList cNodes = doc.getElementsByTagName("card");
        int total = cNodes.getLength();

        for (int i = 0; i < cNodes.getLength(); i++) {
            Element c = (Element) cNodes.item(i);
            String lessonId = c.getAttribute("lessonid");
            String[] lesson = lessons.get(lessonId);
            if (lesson == null) {
                errores.add("Card " + (i + 1) + ": lessonid '" + lessonId + "' sin lesson asociada");
                continue;
            }
            try {
                String grupoId   = lesson[0];
                String materiaId = lesson[1];
                String profId    = lesson[2];
                String aulaId    = firstNonBlank(firstId(c.getAttribute("classroomids")), lesson[3]);
                String daysBits  = firstNonBlank(c.getAttribute("days"), c.getAttribute("day"), lesson[4]);
                int dia = bitsToDia(daysBits);

                String periodNum = firstNonBlank(c.getAttribute("period"), c.getAttribute("startperiod"));
                String[] slot = periodos.get(periodNum);
                if (slot == null) {
                    errores.add("Card " + (i + 1) + ": period '" + periodNum + "' no definido en <periods>");
                    continue;
                }

                crearHorarioUseCase.crear(new CrearHorarioUseCase.Command(
                        uuid(grupoId), uuid(materiaId), uuid(profId), uuid(aulaId),
                        cicloId, dia, slot[0], slot[1], "ASC", usuario));
                exitosos++;
            } catch (Exception e) {
                errores.add("Card " + (i + 1) + " (lesson " + lessonId + "): " + e.getMessage());
            }
        }

        return new ImportResult(total, exitosos, errores.size(), eliminados, errores);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static String diaToBits(int dia) {
        char[] bits = new char[NUM_DIAS];
        Arrays.fill(bits, '0');
        if (dia >= 1 && dia <= NUM_DIAS) bits[dia - 1] = '1';
        return new String(bits);
    }

    /** Acepta bit-string ("01000"→2) o número directo ("2"→2). 1=Lunes. */
    private static int bitsToDia(String days) {
        if (days == null || days.isBlank()) return 1;
        String d = days.trim();
        if (d.matches("[01]{2,}")) {
            int idx = d.indexOf('1');
            return idx >= 0 ? idx + 1 : 1;
        }
        try {
            int n = Integer.parseInt(d);
            return (n >= 1 && n <= NUM_DIAS) ? n : Math.max(1, Math.min(NUM_DIAS, n + 1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String normalizaHora(String h) {
        if (h == null || h.isBlank()) return "";
        String[] parts = h.trim().split(":");
        if (parts.length < 2) return h.trim();
        return String.format("%02d:%02d", Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    private static String firstId(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return csv.split(",")[0].trim();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v.trim();
        return "";
    }

    private static UUID uuid(String s) {
        return (s == null || s.isBlank()) ? null : UUID.fromString(s.trim());
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String shortOf(Object clave, Object fallback) {
        if (clave != null && !clave.toString().isBlank()) return clave.toString();
        String f = fallback == null ? "" : fallback.toString();
        return f.length() > 12 ? f.substring(0, 12) : f;
    }

    private static String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
