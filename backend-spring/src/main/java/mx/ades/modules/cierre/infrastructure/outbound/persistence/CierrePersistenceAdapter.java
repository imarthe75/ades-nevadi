package mx.ades.modules.cierre.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.cierre.domain.port.out.CierreRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia que implementa {@link CierreRepositoryPort} accediendo
 * a la tabla {@code ades_ciclos_escolares} vía JDBC.
 *
 * <p>El método {@code marcarCerrado} actualiza el estado a {@code 'CERRADO'} y
 * desactiva la vigencia del ciclo en una sola sentencia.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class CierrePersistenceAdapter implements CierreRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<String> fetchEstado(UUID cicloId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT estado FROM ades_ciclos_escolares WHERE id = ? AND is_active = TRUE", cicloId);
        if (rows.isEmpty()) return Optional.empty();
        return Optional.ofNullable((String) rows.get(0).get("estado"));
    }

    /**
     * @Transactional aquí en el adapter (no en CierreApplicationService.cerrar) a
     * propósito: cerrar() también invoca CierreCicloService.cerrarCiclo, que exige
     * @Transactional(isolation = SERIALIZABLE) — envolver cerrar() entero con el
     * default (sin isolation explícito) haría que esa llamada anidada intente unirse
     * a una transacción con un isolation level distinto y Spring lance
     * IllegalTransactionStateException. Aislar el @Transactional aquí evita el choque.
     */
    @Override
    @Transactional
    public void marcarCerrado(UUID cicloId) {
        jdbc.update(
            "UPDATE ades_ciclos_escolares SET estado = 'CERRADO', es_vigente = FALSE, fecha_modificacion = now() WHERE id = ?",
            cicloId);
    }
}
