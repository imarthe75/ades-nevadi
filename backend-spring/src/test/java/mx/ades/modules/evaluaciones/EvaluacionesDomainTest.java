package mx.ades.modules.evaluaciones;

import mx.ades.modules.evaluaciones.domain.model.EstatusEntrega;
import mx.ades.modules.evaluaciones.domain.model.ItemCalificacion;
import mx.ades.modules.evaluaciones.domain.model.TipoItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EvaluacionesDomainTest {

    // ── EstatusEntrega ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.puedeCalificarse={1}")
    @CsvSource({
            "PENDIENTE, false",
            "ENTREGADA, true",
            "CALIFICADA, false",
            "EXCUSA, false",
    })
    void estatusEntrega_puedeCalificarse(EstatusEntrega estatus, boolean esperado) {
        // Regla unificada 2026-07-15 (hallazgo Antigravity D4): solo ENTREGADA es
        // calificable, igual que el enum gemelo del módulo entregas.
        assertEquals(esperado, estatus.puedeCalificarse());
    }

    @Test
    void estatusEntrega_calificada_estaCalificada() {
        assertTrue(EstatusEntrega.CALIFICADA.estaCalificada());
        assertFalse(EstatusEntrega.PENDIENTE.estaCalificada());
        assertFalse(EstatusEntrega.ENTREGADA.estaCalificada());
    }

    // ── TipoItem ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.requiereEntregaArchivo={1}")
    @CsvSource({
            "TAREA, true",
            "PROYECTO, true",
            "EXAMEN, false",
            "OTRO, false",
            "PARTICIPACION, false",
    })
    void tipoItem_requiereEntregaArchivo(TipoItem tipo, boolean esperado) {
        assertEquals(esperado, tipo.requiereEntregaArchivo());
    }

    @ParameterizedTest(name = "{0}.esPuntualEnAula={1}")
    @CsvSource({
            "EXAMEN, true",
            "PARTICIPACION, true",
            "TAREA, false",
            "PROYECTO, false",
            "OTRO, false",
    })
    void tipoItem_esPuntualEnAula(TipoItem tipo, boolean esperado) {
        assertEquals(esperado, tipo.esPuntualEnAula());
    }

    // ── ItemCalificacion ─────────────────────────────────────────────────────

    @Test
    void itemCalificacion_calificacionNegativa_lanzaExcepcion() {
        UUID estudianteId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new ItemCalificacion(estudianteId, new BigDecimal("-1"), null));
    }

    @Test
    void itemCalificacion_estudianteIdNull_lanzaExcepcion() {
        // Fase 5: NPE -> IllegalArgumentException (GlobalExceptionHandler mapea 400 solo
        // a IllegalArgumentException; una NPE caía como 500 genérico).
        assertThrows(IllegalArgumentException.class,
                () -> new ItemCalificacion(null, BigDecimal.TEN, null));
    }

    @Test
    void itemCalificacion_excedePuntajeMaximo_correcto() {
        var item = new ItemCalificacion(UUID.randomUUID(), new BigDecimal("9.5"), "ok");
        assertFalse(item.excedePuntajeMaximo(new BigDecimal("10")));
        assertTrue(item.excedePuntajeMaximo(new BigDecimal("9")));
        assertFalse(item.excedePuntajeMaximo(null)); // sin puntaje máximo, no excede
    }

    @Test
    void itemCalificacion_igualAlMaximo_noExcede() {
        var item = new ItemCalificacion(UUID.randomUUID(), new BigDecimal("10"), null);
        assertFalse(item.excedePuntajeMaximo(new BigDecimal("10")));
    }
}
