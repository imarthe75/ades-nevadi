package mx.ades.modules.asistencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClaseRepository extends JpaRepository<Clase, UUID> {
    List<Clase> findByGrupoIdAndFechaClase(UUID grupoId, LocalDate fechaClase);

    List<Clase> findByGrupoIdOrderByFechaClaseDescHoraInicioAsc(UUID grupoId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Clase c WHERE " +
            "(:grupoId IS NULL OR c.grupoId = :grupoId) AND " +
            "(:materiaId IS NULL OR c.materiaId = :materiaId) AND " +
            "(:profesorId IS NULL OR c.profesorId = :profesorId) AND " +
            "(:fechaDesde IS NULL OR c.fechaClase >= :fechaDesde) AND " +
            "(:fechaHasta IS NULL OR c.fechaClase <= :fechaHasta) AND " +
            "(:estatus IS NULL OR c.estatusClase = :estatus) " +
            "ORDER BY c.fechaClase DESC, c.horaInicio ASC")
    List<Clase> findFiltered(
            @org.springframework.data.repository.query.Param("grupoId") UUID grupoId,
            @org.springframework.data.repository.query.Param("materiaId") UUID materiaId,
            @org.springframework.data.repository.query.Param("profesorId") UUID profesorId,
            @org.springframework.data.repository.query.Param("fechaDesde") LocalDate fechaDesde,
            @org.springframework.data.repository.query.Param("fechaHasta") LocalDate fechaHasta,
            @org.springframework.data.repository.query.Param("estatus") String estatus
    );
}
