package mx.ades.modules.cierre.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.cierre.domain.port.out.CierreRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    public void marcarCerrado(UUID cicloId) {
        jdbc.update(
            "UPDATE ades_ciclos_escolares SET estado = 'CERRADO', es_vigente = FALSE, fecha_modificacion = now() WHERE id = ?",
            cicloId);
    }
}
