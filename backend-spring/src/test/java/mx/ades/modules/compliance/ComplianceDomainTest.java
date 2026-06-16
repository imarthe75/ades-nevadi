package mx.ades.modules.compliance;

import mx.ades.modules.compliance.domain.model.EstadoAlerta;
import mx.ades.modules.compliance.domain.model.SeveridadAlerta;
import mx.ades.modules.compliance.domain.port.in.CrearAlertaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarNormativaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarRetencionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceDomainTest {

    // ── SeveridadAlerta ───────────────────────────────────────────────────────

    @Test
    void severidad_of_default_media() {
        assertEquals(SeveridadAlerta.MEDIA, SeveridadAlerta.of(null));
        assertEquals(SeveridadAlerta.MEDIA, SeveridadAlerta.of(""));
    }

    @Test
    void severidad_of_caseInsensitive() {
        assertEquals(SeveridadAlerta.CRITICA, SeveridadAlerta.of("critica"));
        assertEquals(SeveridadAlerta.ALTA, SeveridadAlerta.of("ALTA"));
    }

    @Test
    void severidad_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> SeveridadAlerta.of("EXTREMA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severidad_alerta inválida");
    }

    @ParameterizedTest(name = "{0}.esUrgente={1}")
    @CsvSource({"BAJA, false", "MEDIA, false", "ALTA, true", "CRITICA, true"})
    void severidad_esUrgente(SeveridadAlerta sev, boolean esperado) {
        assertEquals(esperado, sev.esUrgente());
    }

    // ── EstadoAlerta ──────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.esFinal={1}")
    @CsvSource({"PENDIENTE, false", "EN_PROCESO, false", "RESUELTA, true", "CANCELADA, true"})
    void estadoAlerta_esFinal(EstadoAlerta estado, boolean esperado) {
        assertEquals(esperado, estado.esFinal());
    }

    @Test
    void estadoAlerta_permiteAccion() {
        assertTrue(EstadoAlerta.PENDIENTE.permiteAccion());
        assertFalse(EstadoAlerta.RESUELTA.permiteAccion());
    }

    @Test
    void estadoAlerta_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> EstadoAlerta.of("IGNORADA"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── RegistrarNormativaUseCase.Command ─────────────────────────────────────

    @Test
    void command_normativa_sinNombre_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarNormativaUseCase.Command(
                null, "REGLAMENTO", "Desc", LocalDate.now(), null,
                null, true, true, true, "admin", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void command_normativa_nivelInsuficiente_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarNormativaUseCase.Command(
                "Reglamento Interno", "REGLAMENTO", "Desc", LocalDate.now(), null,
                null, true, true, true, "docente", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Admin");
    }

    @Test
    void command_normativa_valido_noLanzaExcepcion() {
        assertThatCode(() -> new RegistrarNormativaUseCase.Command(
                "Reglamento Interno 2026", "REGLAMENTO", "Reglamento general del plantel",
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31),
                "https://ades.setag.mx/docs/reglamento.pdf",
                true, true, false, "dir", 2))
                .doesNotThrowAnyException();
    }

    // ── RegistrarRetencionUseCase.Command ─────────────────────────────────────

    @Test
    void command_retencion_sinAlumnoId_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarRetencionUseCase.Command(
                null, "DOCUMENTOS", "Sin documentos", LocalDate.now(), null,
                null, null, "dir", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alumno_id");
    }

    // ── CrearAlertaUseCase.Command ────────────────────────────────────────────

    @Test
    void command_alerta_sinTipo_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearAlertaUseCase.Command(
                "", "Descripción de la alerta", null, null,
                SeveridadAlerta.ALTA, true, "dir", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_alerta");
    }

    @Test
    void command_alerta_valido_noLanzaExcepcion() {
        assertThatCode(() -> new CrearAlertaUseCase.Command(
                "CERTIFICADOS_PENDIENTES", "Alumnos sin certificado de primer año",
                null, UUID.randomUUID(), SeveridadAlerta.MEDIA, true, "sec", 2))
                .doesNotThrowAnyException();
    }
}
