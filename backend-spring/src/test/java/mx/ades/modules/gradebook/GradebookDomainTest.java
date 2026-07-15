package mx.ades.modules.gradebook;

import mx.ades.modules.gradebook.domain.model.AjusteManual;
import mx.ades.modules.gradebook.domain.model.CalificacionEstado;
import mx.ades.modules.gradebook.domain.port.in.CerrarCalificacionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GradebookDomainTest {

    // ── AjusteManual ──────────────────────────────────────────────────────────

    @Test
    void ajusteManual_justificacionCorta_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new AjusteManual(BigDecimal.ONE, "corta"));
    }

    @Test
    void ajusteManual_justificacionNula_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new AjusteManual(BigDecimal.ONE, null));
    }

    @Test
    void ajusteManual_valorNulo_lanzaExcepcion() {
        // Fase 5: NPE -> IllegalArgumentException (GlobalExceptionHandler mapea 400 solo
        // a IllegalArgumentException; una NPE caía como 500 genérico).
        assertThrows(IllegalArgumentException.class,
                () -> new AjusteManual(null, "justificacion suficientemente larga aqui"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Esta es la justificacion suficiente",
        "Corrección por error de captura del docente 2026"
    })
    void ajusteManual_justificacionValida_creaObjeto(String justificacion) {
        assertDoesNotThrow(() -> new AjusteManual(BigDecimal.ONE, justificacion));
    }

    @Test
    void ajusteManual_calcularFinal_sumaCorrectamente() {
        AjusteManual ajuste = new AjusteManual(new BigDecimal("1.5"), "justificacion suficientemente larga valida");
        BigDecimal final_ = ajuste.calcularFinal(new BigDecimal("8.0"));
        assertEquals(new BigDecimal("9.50"), final_);
    }

    @Test
    void ajusteManual_calcularFinal_calBaseNula_usaCero() {
        AjusteManual ajuste = new AjusteManual(new BigDecimal("2.0"), "justificacion suficientemente larga valida");
        assertEquals(new BigDecimal("2.00"), ajuste.calcularFinal(null));
    }

    @Test
    void ajusteManual_justificacionTrimeada() {
        AjusteManual ajuste = new AjusteManual(BigDecimal.ONE, "  justificacion con espacios al inicio   ");
        assertEquals("justificacion con espacios al inicio", ajuste.justificacion());
    }

    // ── CalificacionEstado ────────────────────────────────────────────────────

    @Test
    void calificacionEstado_abierta_permiteAjusteSinAdmin() {
        CalificacionEstado estado = new CalificacionEstado(UUID.randomUUID(), BigDecimal.TEN, false);
        assertTrue(estado.permiteAjuste(false));
        assertTrue(estado.permiteAjuste(true));
    }

    @Test
    void calificacionEstado_cerrada_soloAdminPuedeAjustar() {
        CalificacionEstado estado = new CalificacionEstado(UUID.randomUUID(), BigDecimal.TEN, true);
        assertFalse(estado.permiteAjuste(false));
        assertTrue(estado.permiteAjuste(true));
    }

    // ── CerrarCalificacionUseCase.Command ─────────────────────────────────────

    @Test
    void cerrarCommand_rolesAutorizados_tienePermiso() {
        var cmd = new CerrarCalificacionUseCase.Command(
                UUID.randomUUID(), "director@nevadi", Set.of("DIRECTOR"));
        assertTrue(cmd.tienePermiso());
    }

    @Test
    void cerrarCommand_rolSinPermiso_notienePermiso() {
        var cmd = new CerrarCalificacionUseCase.Command(
                UUID.randomUUID(), "docente@nevadi", Set.of("DOCENTE"));
        assertFalse(cmd.tienePermiso());
    }

    @Test
    void cerrarCommand_adminGlobal_tienePermiso() {
        var cmd = new CerrarCalificacionUseCase.Command(
                UUID.randomUUID(), "admin@nevadi", Set.of("ADMIN_GLOBAL"));
        assertTrue(cmd.tienePermiso());
    }

    @Test
    void cerrarCommand_sinRoles_notienePermiso() {
        var cmd = new CerrarCalificacionUseCase.Command(
                UUID.randomUUID(), "user@nevadi", Set.of());
        assertFalse(cmd.tienePermiso());
    }
}
