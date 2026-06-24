package mx.ades.modules.aulas.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.aulas.Aula;
import mx.ades.modules.aulas.domain.port.in.ActualizarAulaUseCase;
import mx.ades.modules.aulas.domain.port.in.CrearAulaUseCase;
import mx.ades.modules.aulas.domain.port.out.AulaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Caso de uso: creación y actualización de aulas físicas en los planteles.
 * Implementa {@link CrearAulaUseCase} y {@link ActualizarAulaUseCase} coordinando
 * el dominio de aulas con el puerto de repositorio, gestionando atributos
 * extendidos como equipamiento, capacidad y estado del espacio.
 *
 * @author ADES
 * @since 2026
 */
@Service
public class AulaApplicationService implements CrearAulaUseCase, ActualizarAulaUseCase {

    private final AulaRepositoryPort repositoryPort;

    public AulaApplicationService(AulaRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public Map<String, Object> crear(CrearAulaUseCase.Command cmd) {
        Aula a = new Aula();
        a.setNombreAula(cmd.nombreAula().trim());
        a.setPlantelId(cmd.plantelId());
        a.setTipoAula(cmd.tipoAula() != null ? cmd.tipoAula() : "AULA");
        a.setCapacidadAlumnos(cmd.capacidadAlumnos());
        a.setIsActive(true);
        Aula saved = repositoryPort.save(a);
        return Map.of("id", saved.getId(), "nombre_aula", saved.getNombreAula());
    }

    @Override
    public Map<String, Object> actualizar(ActualizarAulaUseCase.Command cmd) {
        Aula a = repositoryPort.findById(cmd.aulaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada"));

        a.setNombreAula(cmd.nombreAula().trim());
        if (cmd.plantelId() != null)         a.setPlantelId(cmd.plantelId());
        if (cmd.tipoAula() != null)          a.setTipoAula(cmd.tipoAula());
        if (cmd.capacidadAlumnos() != null)  a.setCapacidadAlumnos(cmd.capacidadAlumnos());
        if (cmd.isActive() != null)          a.setIsActive(cmd.isActive());
        // campos extendidos
        if (cmd.claveAula() != null)         a.setClaveAula(cmd.claveAula());
        if (cmd.piso() != null)              a.setPiso(cmd.piso());
        if (cmd.edificio() != null)          a.setEdificio(cmd.edificio());
        if (cmd.capacidadMaxima() != null)   a.setCapacidadMaxima(cmd.capacidadMaxima());
        if (cmd.tieneProyector() != null)    a.setTieneProyector(cmd.tieneProyector());
        if (cmd.tienePizarraDigital() != null) a.setTienePizarraDigital(cmd.tienePizarraDigital());
        if (cmd.tienePizarron() != null)     a.setTienePizarron(cmd.tienePizarron());
        if (cmd.tieneAireAcondicionado() != null) a.setTieneAireAcondicionado(cmd.tieneAireAcondicionado());
        if (cmd.tieneVentiladores() != null) a.setTieneVentiladores(cmd.tieneVentiladores());
        if (cmd.tieneInternet() != null)     a.setTieneInternet(cmd.tieneInternet());
        if (cmd.numComputadoras() != null)   a.setNumComputadoras(cmd.numComputadoras());
        if (cmd.estadoAula() != null)        a.setEstadoAula(cmd.estadoAula());
        if (cmd.observaciones() != null)     a.setObservaciones(cmd.observaciones());
        repositoryPort.save(a);
        return Map.of("updated", true);
    }
}
