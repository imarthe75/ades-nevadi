package mx.ades.modules.licencias;

import mx.ades.modules.licencias.domain.model.DiasHabiles;
import mx.ades.modules.licencias.domain.model.EstadoLicencia;
import mx.ades.modules.licencias.domain.model.TipoLicencia;
import mx.ades.modules.licencias.domain.port.in.ResolverLicenciaUseCase;
import mx.ades.modules.licencias.domain.port.in.SolicitarLicenciaUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class LicenciasDomainTest {

    // ── TipoLicencia ──────────────────────────────────────────────────────────

    @Test
    void tipoLicencia_of_caseInsensitive() {
        assertEquals(TipoLicencia.MEDICA, TipoLicencia.of("medica"));
        assertEquals(TipoLicencia.MATERNIDAD, TipoLicencia.of("MATERNIDAD"));
        assertEquals(TipoLicencia.CAPACITACION, TipoLicencia.of("Capacitacion"));
    }

    @Test
    void tipoLicencia_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoLicencia.of("VACACIONES"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_licencia inválido");
    }

    @Test
    void tipoLicencia_maternidadPaternidad_esMaternoPaternal() {
        assertTrue(TipoLicencia.MATERNIDAD.esMaternoPaternal());
        assertTrue(TipoLicencia.PATERNIDAD.esMaternoPaternal());
        assertFalse(TipoLicencia.MEDICA.esMaternoPaternal());
    }

    @Test
    void tipoLicencia_comisionCapacitacion_esLaboralInstitucional() {
        assertTrue(TipoLicencia.COMISION.esLaboralInstitucional());
        assertTrue(TipoLicencia.CAPACITACION.esLaboralInstitucional());
        assertFalse(TipoLicencia.DUELO.esLaboralInstitucional());
    }

    // ── EstadoLicencia ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.permiteModificacion={1}")
    @CsvSource({
            "PENDIENTE, true",
            "APROBADA, false",
            "RECHAZADA, false",
            "CANCELADA, false",
    })
    void estadoLicencia_permiteModificacion(EstadoLicencia estado, boolean esperado) {
        assertEquals(esperado, estado.permiteModificacion());
    }

    @Test
    void estadoLicencia_esResuelto() {
        assertTrue(EstadoLicencia.APROBADA.esResuelto());
        assertTrue(EstadoLicencia.RECHAZADA.esResuelto());
        assertTrue(EstadoLicencia.CANCELADA.esResuelto());
        assertFalse(EstadoLicencia.PENDIENTE.esResuelto());
    }

    // ── DiasHabiles ───────────────────────────────────────────────────────────

    @Test
    void diasHabiles_semanaCompleta_5dias() {
        // Monday to Friday = 5 working days
        LocalDate lunes = LocalDate.of(2026, 6, 15);
        LocalDate viernes = LocalDate.of(2026, 6, 19);
        assertEquals(5, DiasHabiles.calcular(lunes, viernes).valor());
    }

    @Test
    void diasHabiles_diaUnico_1dia() {
        LocalDate hoy = LocalDate.of(2026, 6, 15); // Monday
        assertEquals(1, DiasHabiles.calcular(hoy, hoy).valor());
    }

    @Test
    void diasHabiles_finDeSemana_minimoUno() {
        // Saturday to Sunday = 0 working days → returns 1 (minimum)
        LocalDate sabado = LocalDate.of(2026, 6, 20);
        LocalDate domingo = LocalDate.of(2026, 6, 21);
        assertEquals(1, DiasHabiles.calcular(sabado, domingo).valor());
    }

    @Test
    void diasHabiles_dosSemanasConFinSemana_10dias() {
        LocalDate lunes1 = LocalDate.of(2026, 6, 15);
        LocalDate viernes2 = LocalDate.of(2026, 6, 26);
        assertEquals(10, DiasHabiles.calcular(lunes1, viernes2).valor());
    }

    @Test
    void diasHabiles_fechaFinAnteriorInicio_lanzaExcepcion() {
        assertThatThrownBy(() -> DiasHabiles.calcular(
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha_fin");
    }

    // ── SolicitarLicenciaUseCase.Command ──────────────────────────────────────

    @Test
    void command_solicitar_sinPersonalId_lanzaExcepcion() {
        assertThatThrownBy(() -> new SolicitarLicenciaUseCase.Command(
                null, TipoLicencia.MEDICA,
                LocalDate.now(), LocalDate.now().plusDays(3),
                "reposo", null, true, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("personal_id");
    }

    @Test
    void command_solicitar_fechaFinAntes_lanzaExcepcion() {
        assertThatThrownBy(() -> new SolicitarLicenciaUseCase.Command(
                UUID.randomUUID(), TipoLicencia.MEDICA,
                LocalDate.now().plusDays(5), LocalDate.now(),
                "reposo", null, true, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha_fin");
    }

    // ── ResolverLicenciaUseCase.Command ───────────────────────────────────────

    @Test
    void command_resolver_rechazar_sinObservaciones_lanzaExcepcion() {
        assertThatThrownBy(() -> new ResolverLicenciaUseCase.Command(
                UUID.randomUUID(), ResolverLicenciaUseCase.Accion.RECHAZAR,
                null, UUID.randomUUID(), "director", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observaciones");
    }

    @Test
    void command_resolver_aprobar_sinObservaciones_esValido() {
        assertThatCode(() -> new ResolverLicenciaUseCase.Command(
                UUID.randomUUID(), ResolverLicenciaUseCase.Accion.APROBAR,
                null, UUID.randomUUID(), "director", 2))
                .doesNotThrowAnyException();
    }
}
