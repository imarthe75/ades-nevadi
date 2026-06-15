package mx.ades.modules.alumnos;

import mx.ades.modules.alumnos.query.AlumnoQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlumnoQueryServiceTest {

    @Mock JdbcTemplate jdbc;

    AlumnoQueryService service;

    @BeforeEach
    void setUp() {
        service = new AlumnoQueryService(jdbc);
    }

    @Test
    void listar_sinFiltros_devuelveMapConDataYTotal() {
        List<Map<String, Object>> rows = List.of(
                Map.of("id", UUID.randomUUID(), "matricula", "A001"),
                Map.of("id", UUID.randomUUID(), "matricula", "A002"));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(rows);

        Map<String, Object> resultado = service.listar(null);

        assertEquals(2, resultado.get("total"));
        assertEquals(rows, resultado.get("data"));
    }

    @Test
    void listar_conPlantelId_pasaParametro() {
        UUID plantelId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.listar(plantelId);

        verify(jdbc).query(contains("e.plantel_id = ?"), any(RowMapper.class), eq(new Object[]{plantelId}));
    }

    @Test
    void obtener_alumnoNoExistente_lanzaNotFound() {
        when(jdbc.queryForList(anyString(), any(UUID.class))).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.obtener(UUID.randomUUID()));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void obtener_alumnoExistente_devuelveDatos() {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("id", id); row.put("matricula", "A001");
        when(jdbc.queryForList(anyString(), eq(id))).thenReturn(List.of(row));

        Map<String, Object> result = service.obtener(id);

        assertEquals("A001", result.get("matricula"));
    }

    @Test
    void resolverPersonaId_alumnoNoExistente_lanzaNotFound() {
        when(jdbc.queryForList(anyString(), any(UUID.class))).thenReturn(List.of());

        assertThrows(ResponseStatusException.class,
                () -> service.resolverPersonaId(UUID.randomUUID()));
    }

    @Test
    void resolverPersonaId_alumnoExistente_devuelvePersonaId() {
        UUID personaId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), any(UUID.class)))
                .thenReturn(List.of(Map.of("persona_id", personaId)));

        UUID result = service.resolverPersonaId(UUID.randomUUID());

        assertEquals(personaId, result);
    }
}
