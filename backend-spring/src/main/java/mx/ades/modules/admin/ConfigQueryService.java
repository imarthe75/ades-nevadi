package mx.ades.modules.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfigQueryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    public List<Map<String, Object>> listarConfig(String grupo) {
        if (grupo != null && !grupo.isBlank()) {
            return jdbc.queryForList(
                "SELECT id, clave, valor::text AS valor, descripcion, grupo, tipo_valor, es_editable " +
                "FROM ades_config WHERE grupo = ? ORDER BY clave",
                grupo);
        }
        return jdbc.queryForList(
            "SELECT id, clave, valor::text AS valor, descripcion, grupo, tipo_valor, es_editable " +
            "FROM ades_config ORDER BY grupo, clave");
    }

    @Transactional
    public Map<String, Object> actualizarConfig(String clave, Object nuevoValor) {
        String jsonValor;
        try {
            jsonValor = om.writeValueAsString(nuevoValor);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido");
        }
        int updated = jdbc.update(
            "UPDATE ades_config SET valor = ?::jsonb WHERE clave = ? AND es_editable = true",
            jsonValor, clave);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Config '" + clave + "' no encontrada o no es editable");
        }
        return jdbc.queryForList(
            "SELECT id, clave, valor::text AS valor, descripcion, grupo, tipo_valor, es_editable " +
            "FROM ades_config WHERE clave = ?", clave).stream().findFirst()
            .orElseThrow();
    }

    public List<Map<String, Object>> listarEscalasCualitativas() {
        return jdbc.queryForList(
            "SELECT id, nombre, nivel_educativo, descripcion, valores_json::text AS valores_json, is_active " +
            "FROM ades_escalas_evaluacion ORDER BY nivel_educativo, nombre");
    }

    @Transactional
    public Map<String, Object> actualizarEscala(String id, Map<String, Object> body) {
        String valoresJson;
        Object vals = body.get("valores_json");
        try {
            valoresJson = om.writeValueAsString(vals);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valores_json inválido");
        }

        String nombre      = body.getOrDefault("nombre", "").toString();
        String descripcion = body.getOrDefault("descripcion", "").toString();
        Boolean isActive   = vals != null && body.containsKey("is_active")
            ? Boolean.valueOf(body.get("is_active").toString()) : null;

        int updated = jdbc.update(
            "UPDATE ades_escalas_evaluacion SET nombre = COALESCE(NULLIF(?, ''), nombre), " +
            "descripcion = COALESCE(NULLIF(?, ''), descripcion), " +
            "valores_json = ?::jsonb, " +
            "is_active = COALESCE(?, is_active) " +
            "WHERE id = ?::uuid",
            nombre, descripcion, valoresJson, isActive, id);

        if (updated == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Escala no encontrada");

        return jdbc.queryForList(
            "SELECT id, nombre, nivel_educativo, descripcion, valores_json::text AS valores_json, is_active " +
            "FROM ades_escalas_evaluacion WHERE id = ?::uuid", id)
            .stream().findFirst().orElseThrow();
    }

    /** Retorna la config de evaluación cualitativa + la escala NEM activa para el nivel dado. */
    public Map<String, Object> configCualitativa(String nivelEducativo) {
        List<Map<String, Object>> configs = listarConfig("evaluacion_cualitativa");

        Map<String, Object> configMap = new java.util.LinkedHashMap<>();
        for (Map<String, Object> c : configs) {
            String clave = c.get("clave").toString();
            String valorStr = c.get("valor").toString();
            Object valorParsed;
            try {
                valorParsed = om.readValue(valorStr, Object.class);
            } catch (Exception e) {
                valorParsed = valorStr;
            }
            configMap.put(clave, valorParsed);
        }

        // Obtener la escala activa para el nivel
        String nivel = nivelEducativo != null ? nivelEducativo.toUpperCase() : "PRIMARIA";
        List<Map<String, Object>> escalas = jdbc.queryForList(
            "SELECT id, nombre, valores_json::text AS valores_json " +
            "FROM ades_escalas_evaluacion " +
            "WHERE nivel_educativo = ? AND is_active = true " +
            "ORDER BY fecha_creacion DESC LIMIT 1", nivel);

        Map<String, Object> escala = escalas.isEmpty() ? null : escalas.get(0);
        if (escala != null) {
            try {
                Object parsedVals = om.readValue(escala.get("valores_json").toString(),
                    new TypeReference<List<Object>>() {});
                escala.put("valores_json", parsedVals);
            } catch (Exception ignored) {}
        }

        return Map.of("config", configMap, "escala", escala != null ? escala : Map.of());
    }
}
