package mx.ades.modules.disponibilidad.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.disponibilidad.domain.model.DiaSemana;
import mx.ades.modules.disponibilidad.domain.port.out.DisponibilidadRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;

/**
 * Servicio de lectura CQRS para el módulo disponibilidad.
 * <p>Enriquece los slots con el nombre legible del día (via {@link DiaSemana}),
 * calcula el resumen de horas semanales disponibles y el reporte de cobertura docente.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadQueryService {

    private final DisponibilidadRepositoryPort repo;

    public List<Map<String, Object>> listar(UUID profesorId, UUID cicloEscolarId, String q, UUID plantelId) {
        List<Map<String, Object>> rows = repo.list(profesorId, cicloEscolarId, q, plantelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> d = new HashMap<>(r);
            int dia = ((Number) d.get("dia_semana")).intValue();
            d.put("dia_nombre", DiaSemana.nombreDeIndice(dia));
            if (d.get("hora_inicio") != null) d.put("hora_inicio", d.get("hora_inicio").toString());
            if (d.get("hora_fin") != null) d.put("hora_fin", d.get("hora_fin").toString());
            result.add(d);
        }
        return result;
    }

    public UUID plantelDeProfesor(UUID profesorId) {
        return repo.plantelDeProfesor(profesorId);
    }

    public Map<String, Object> resumen(UUID profesorId, UUID cicloEscolarId) {
        List<Map<String, Object>> slots = repo.resumen(profesorId, cicloEscolarId);
        double horas = 0.0;
        Set<Integer> diasSet = new TreeSet<>();
        int totalSlots = slots.size();
        int slotsDisponibles = 0;

        for (Map<String, Object> s : slots) {
            if (Boolean.TRUE.equals(s.get("disponible"))) {
                slotsDisponibles++;
                LocalTime hi = (LocalTime) s.get("hora_inicio");
                LocalTime hf = (LocalTime) s.get("hora_fin");
                int mins = (hf.getHour() * 60 + hf.getMinute()) - (hi.getHour() * 60 + hi.getMinute());
                horas += Math.max(mins, 0) / 60.0;
                diasSet.add(((Number) s.get("dia_semana")).intValue());
            }
        }

        Map<String, Object> prof = repo.getProfesorHoras(profesorId);

        List<String> diasDisponibles = new ArrayList<>();
        for (int d : diasSet) diasDisponibles.add(DiaSemana.nombreDeIndice(d));

        Map<String, Object> response = new HashMap<>();
        response.put("profesor_id", profesorId);
        response.put("dias_disponibles", diasDisponibles);
        response.put("total_slots", totalSlots);
        response.put("slots_disponibles", slotsDisponibles);
        response.put("horas_semana", Math.round(horas * 10.0) / 10.0);
        response.put("horas_semana_max", prof.get("horas_semana_max") != null ? prof.get("horas_semana_max") : 20.0);
        response.put("horas_frente_grupo", prof.get("horas_frente_grupo") != null ? prof.get("horas_frente_grupo") : 16.0);
        return response;
    }

    public List<Map<String, Object>> cobertura(UUID cicloId, UUID plantelId) {
        List<Map<String, Object>> rows = repo.cobertura(cicloId, plantelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long slots = ((Number) r.get("slots_registrados")).longValue();
            Map<String, Object> item = new HashMap<>();
            item.put("profesor_id", r.get("id"));
            item.put("nombre_completo", r.get("apellido_paterno") + " " + r.get("nombre"));
            item.put("slots_registrados", slots);
            item.put("tiene_disponibilidad", slots > 0);
            result.add(item);
        }
        return result;
    }
}
