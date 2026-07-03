package mx.ades.modules.planteles;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Upsert de clave oficial (CCT SEP / incorporación UAEMEX) por plantel+nivel.
 * Tabla independiente de ades_planteles.clave_ct (deprecado) porque un mismo
 * plantel físico puede tener CCT distintos por nivel educativo (AC/CCT — mig 103).
 */
@Component
public class PlantelClaveWriteService {

    private final JdbcTemplate jdbc;

    public PlantelClaveWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void actualizar(UUID plantelId, UUID nivelEducativoId, Map<String, Object> body) {
        String tipoClave = (String) body.getOrDefault("tipo_clave", "CCT_SEP");
        String clave = (String) body.get("clave");
        String observaciones = (String) body.get("observaciones");

        int updated = jdbc.update("""
            UPDATE ades_plantel_nivel_clave
               SET clave = ?, observaciones = ?
             WHERE plantel_id = ? AND nivel_educativo_id = ? AND tipo_clave = ?
            """, clave, observaciones, plantelId, nivelEducativoId, tipoClave);

        if (updated == 0) {
            jdbc.update("""
                INSERT INTO ades_plantel_nivel_clave
                    (plantel_id, nivel_educativo_id, tipo_clave, clave, observaciones)
                VALUES (?, ?, ?, ?, ?)
                """, plantelId, nivelEducativoId, tipoClave, clave, observaciones);
        }
    }
}
