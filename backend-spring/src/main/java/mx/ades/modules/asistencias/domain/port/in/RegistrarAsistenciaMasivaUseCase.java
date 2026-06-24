package mx.ades.modules.asistencias.domain.port.in;

import java.util.List;

/**
 * Puerto de entrada: define el contrato para registrar masivamente las asistencias
 * de todos los alumnos de una clase en el dominio de asistencias.
 *
 * <p>Utiliza upsert ({@code ON CONFLICT}) para idempotencia en envíos repetidos.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarAsistenciaMasivaUseCase {
    void ejecutar(List<RegistrarAsistenciaCommand> comandos, String usuarioCreacion);
}
