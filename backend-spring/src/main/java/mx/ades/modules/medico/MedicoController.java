package mx.ades.modules.medico;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MedicoController {

    private final ExpedienteMedicoRepository expedienteRepository;
    private final IncidenteMedicoRepository incidenteRepository;

    @GetMapping("/expedientes-medicos/alumno/{estudianteId}")
    public ResponseEntity<ExpedienteMedico> obtenerExpediente(@PathVariable("estudianteId") UUID estudianteId) {
        ExpedienteMedico exp = expedienteRepository.findByEstudianteId(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente médico no encontrado"));
        return ResponseEntity.ok(exp);
    }

    @PostMapping("/expedientes-medicos")
    public ResponseEntity<ExpedienteMedico> crearExpediente(@RequestBody ExpedienteMedico data) {
        expedienteRepository.findByEstudianteId(data.getEstudianteId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El alumno ya tiene expediente médico");
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(expedienteRepository.save(data));
    }

    @PutMapping("/expedientes-medicos/{id}")
    public ResponseEntity<ExpedienteMedico> actualizarExpediente(
            @PathVariable("id") UUID id,
            @RequestBody ExpedienteMedico data) {

        ExpedienteMedico exp = expedienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado"));

        if (data.getTipoSangre() != null) exp.setTipoSangre(data.getTipoSangre());
        if (data.getAlergias() != null) exp.setAlergias(data.getAlergias());
        if (data.getMedicamentosAutorizados() != null) exp.setMedicamentosAutorizados(data.getMedicamentosAutorizados());
        if (data.getCondicionesCronicas() != null) exp.setCondicionesCronicas(data.getCondicionesCronicas());
        if (data.getObservacionesGenerales() != null) exp.setObservacionesGenerales(data.getObservacionesGenerales());
        if (data.getNss() != null) exp.setNss(data.getNss());
        if (data.getDiscapacidad() != null) exp.setDiscapacidad(data.getDiscapacidad());
        if (data.getSeguroMedicoTipo() != null) exp.setSeguroMedicoTipo(data.getSeguroMedicoTipo());
        if (data.getSeguroMedicoNumero() != null) exp.setSeguroMedicoNumero(data.getSeguroMedicoNumero());
        if (data.getVacunasAlDia() != null) exp.setVacunasAlDia(data.getVacunasAlDia());
        if (data.getPadecimientoCronico() != null) exp.setPadecimientoCronico(data.getPadecimientoCronico());
        if (data.getRequiereMedicacion() != null) exp.setRequiereMedicacion(data.getRequiereMedicacion());

        return ResponseEntity.ok(expedienteRepository.save(exp));
    }

    @GetMapping("/incidentes-medicos/alumno/{estudianteId}")
    public ResponseEntity<List<IncidenteMedico>> incidentesAlumno(@PathVariable("estudianteId") UUID estudianteId) {
        return ResponseEntity.ok(incidenteRepository.findByEstudianteIdAndIsActiveTrueOrderByFechaIncidenteDesc(estudianteId));
    }

    @PostMapping("/incidentes-medicos")
    public ResponseEntity<IncidenteMedico> registrarIncidente(@RequestBody IncidenteMedico data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidenteRepository.save(data));
    }
}
