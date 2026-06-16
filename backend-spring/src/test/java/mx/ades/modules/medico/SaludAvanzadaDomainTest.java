package mx.ades.modules.medico;

import mx.ades.modules.medico.domain.port.in.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SaludAvanzadaDomainTest {

    // ── RegistrarMedicamentoUseCase.Command ───────────────────────────────────

    @Test
    void medicamento_sinAlumnoId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarMedicamentoUseCase.Command(null, "Paracetamol", null, null, null, "ORAL", null, null, null, null, 1, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("alumno_id");
    }

    @Test
    void medicamento_sinNombre_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarMedicamentoUseCase.Command(UUID.randomUUID(), "", null, null, null, "ORAL", null, null, null, null, 1, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nombre_medicamento");
    }

    @Test
    void medicamento_nivelSuperior3_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarMedicamentoUseCase.Command(UUID.randomUUID(), "Paracetamol", null, null, null, "ORAL", null, null, null, null, 4, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nivel Médico");
    }

    @Test
    void medicamento_valido_noLanzaExcepcion() {
        assertThatCode(() ->
            new RegistrarMedicamentoUseCase.Command(UUID.randomUUID(), "Paracetamol", "500mg", "cada 8h", "8am", "ORAL", "Dr. García",
                LocalDate.now(), null, null, 2, "user"))
            .doesNotThrowAnyException();
    }

    // ── SuspenderMedicamentoUseCase.Command ───────────────────────────────────

    @Test
    void suspender_sinMedicamentoId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new SuspenderMedicamentoUseCase.Command(null, 1, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("medicamento_id");
    }

    @Test
    void suspender_nivelSuperior3_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new SuspenderMedicamentoUseCase.Command(UUID.randomUUID(), 5, "user"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suspender_valido_noLanzaExcepcion() {
        assertThatCode(() ->
            new SuspenderMedicamentoUseCase.Command(UUID.randomUUID(), 3, "user"))
            .doesNotThrowAnyException();
    }

    // ── GenerarActaIncidenteUseCase.Command ───────────────────────────────────

    @Test
    void acta_sinIncidenteId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new GenerarActaIncidenteUseCase.Command(null, "Descripción", null, null, false, null, true, null, 1, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incidente_id");
    }

    @Test
    void acta_nivelSuperior3_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new GenerarActaIncidenteUseCase.Command(UUID.randomUUID(), "Descripción", null, null, false, null, true, null, 4, "user"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acta_valida_noLanzaExcepcion() {
        assertThatCode(() ->
            new GenerarActaIncidenteUseCase.Command(UUID.randomUUID(), "Descripción detallada", "Testigo1", "Medidas", false, null, true, "Firma", 1, "user"))
            .doesNotThrowAnyException();
    }

    // ── RegistrarPsicosocialUseCase.Command ───────────────────────────────────

    @Test
    void psicosocial_sinAlumnoId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarPsicosocialUseCase.Command(null, "INDIVIDUAL", "motivo", null, null, false, null, null, 1, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("alumno_id");
    }

    @Test
    void psicosocial_valido_noLanzaExcepcion() {
        assertThatCode(() ->
            new RegistrarPsicosocialUseCase.Command(UUID.randomUUID(), "INDIVIDUAL", "Ansiedad", "Obs", "Estrategias", false, null, null, 2, "user"))
            .doesNotThrowAnyException();
    }

    // ── RegistrarTutoriaUseCase.Command ───────────────────────────────────────

    @Test
    void tutoria_sinAlumnoId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarTutoriaUseCase.Command(null, "ACADEMICA", "tema", null, 50, null, null, false, 1, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("alumno_id");
    }

    @Test
    void tutoria_sinTipoTutoria_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarTutoriaUseCase.Command(UUID.randomUUID(), "", "tema", null, 50, null, null, false, 1, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tipo_tutoria");
    }

    @Test
    void tutoria_nivelSuperior3_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarTutoriaUseCase.Command(UUID.randomUUID(), "ACADEMICA", "tema", null, 50, null, null, false, 5, "user"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tutoria_valida_noLanzaExcepcion() {
        assertThatCode(() ->
            new RegistrarTutoriaUseCase.Command(UUID.randomUUID(), "ACADEMICA", "Matemáticas", "Repaso", 50, "Acuerdos", null, false, 1, "user"))
            .doesNotThrowAnyException();
    }
}
