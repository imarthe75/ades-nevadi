package mx.ades.modules.asistencia_personal.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencia_personal.domain.port.out.AsistenciaPersonalRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AsistenciaPersonalQueryService {

    private final AsistenciaPersonalRepositoryPort repository;

    public List<Map<String, Object>> listar(UUID personaId, LocalDate fechaInicio, LocalDate fechaFin,
                                             String tipoJornada, String q) {
        return repository.list(personaId, fechaInicio, fechaFin, tipoJornada, q);
    }

    public Map<String, Object> detalle(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registro de asistencia no encontrado: " + id));
    }

    public Map<String, Object> reporte(UUID personaId, int mes, int anio) {
        List<Map<String, Object>> records = repository.reporte(personaId, mes, anio);

        int totalDias = records.size();
        int diasFalta = 0, diasInc = 0, diasVac = 0, diasPerm = 0, diasAsistio = 0, retardos = 0;

        for (Map<String, Object> r : records) {
            String tj = (String) r.get("tipo_jornada");
            Boolean ret = (Boolean) r.get("es_retardo");
            switch (tj == null ? "" : tj) {
                case "NINGUNA" -> diasFalta++;
                case "INCAPACIDAD" -> diasInc++;
                case "VACACIONES" -> diasVac++;
                case "PERMISO" -> diasPerm++;
                case "COMPLETA", "MEDIA" -> diasAsistio++;
            }
            if (Boolean.TRUE.equals(ret)) retardos++;
        }

        double pct = totalDias > 0
                ? Math.round(((double) diasAsistio / totalDias * 100.0) * 10.0) / 10.0
                : 0.0;

        Map<String, Object> response = new HashMap<>();
        response.put("persona_id", personaId.toString());
        response.put("mes", mes);
        response.put("anio", anio);
        response.put("total_dias", totalDias);
        response.put("dias_asistio", diasAsistio);
        response.put("dias_falta", diasFalta);
        response.put("dias_incapacidad", diasInc);
        response.put("dias_vacaciones", diasVac);
        response.put("dias_permiso", diasPerm);
        response.put("total_retardos", retardos);
        response.put("porcentaje_asistencia", pct);
        return response;
    }
}
