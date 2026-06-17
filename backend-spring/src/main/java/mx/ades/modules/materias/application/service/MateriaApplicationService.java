package mx.ades.modules.materias.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.materias.Materia;
import mx.ades.modules.materias.domain.port.in.ActualizarMateriaUseCase;
import mx.ades.modules.materias.domain.port.in.CrearMateriaUseCase;
import mx.ades.modules.materias.domain.port.out.MateriaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class MateriaApplicationService implements CrearMateriaUseCase, ActualizarMateriaUseCase {

    private final MateriaRepositoryPort repositoryPort;

    public MateriaApplicationService(MateriaRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public Map<String, Object> crear(CrearMateriaUseCase.Command cmd) {
        Materia m = new Materia();
        m.setNombreMateria(cmd.nombreMateria().trim());
        m.setClaveMateria(cmd.claveMateria());
        m.setNivelEducativoId(cmd.nivelEducativoId());
        m.setHorasSemana(cmd.horasSemana());
        m.setEsIngles(cmd.esIngles() != null ? cmd.esIngles() : false);
        m.setIsActive(true);
        Materia saved = repositoryPort.save(m);
        return Map.of("id", saved.getId(), "nombre_materia", saved.getNombreMateria());
    }

    @Override
    public Map<String, Object> actualizar(ActualizarMateriaUseCase.Command cmd) {
        Materia m = repositoryPort.findById(cmd.materiaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada"));

        m.setNombreMateria(cmd.nombreMateria().trim());
        m.setClaveMateria(cmd.claveMateria());
        m.setNivelEducativoId(cmd.nivelEducativoId());
        m.setHorasSemana(cmd.horasSemana());
        if (cmd.esIngles() != null) m.setEsIngles(cmd.esIngles());
        if (cmd.isActive() != null) m.setIsActive(cmd.isActive());
        repositoryPort.save(m);
        return Map.of("updated", true);
    }
}
