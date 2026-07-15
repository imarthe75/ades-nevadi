package mx.ades.modules.personal_admin;

import mx.ades.modules.personal_admin.domain.model.TipoRolPersonal;
import mx.ades.modules.personal_admin.domain.port.in.RegistrarPersonalAdminUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalAdminDomainTest {

    // ── TipoRolPersonal ───────────────────────────────────────────────────────

    @Test
    void tipoRol_of_conocido_retornaEnum() {
        assertEquals(TipoRolPersonal.DIRECTOR, TipoRolPersonal.of("director"));
        assertEquals(TipoRolPersonal.SECRETARIA, TipoRolPersonal.of("SECRETARIA"));
        assertEquals(TipoRolPersonal.PREFECTO, TipoRolPersonal.of("prefecto"));
    }

    @Test
    void tipoRol_of_desconocido_retornaOTRO() {
        assertEquals(TipoRolPersonal.OTRO, TipoRolPersonal.of("JARDINERO"));
        assertEquals(TipoRolPersonal.OTRO, TipoRolPersonal.of("INTENDENTE"));
    }

    @Test
    void tipoRol_of_nulo_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoRolPersonal.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_rol");
    }

    @ParameterizedTest(name = "{0}.esDireccion={1}")
    @CsvSource({"DIRECTOR, true", "SUBDIRECTOR, true", "COORDINADOR, false", "SECRETARIA, false", "OTRO, false"})
    void tipoRol_esDireccion(TipoRolPersonal rol, boolean esperado) {
        assertEquals(esperado, rol.esDireccion());
    }

    // ── RegistrarPersonalAdminUseCase.Command ─────────────────────────────────

    @Test
    void command_sinPlantelId_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarPersonalAdminUseCase.Command(
                null,
                Map.of("nombre", "Juan"),
                Map.of("tipo_rol", "PREFECTO"),
                "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plantel_id");
    }

    @Test
    void command_sinPersona_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarPersonalAdminUseCase.Command(
                UUID.randomUUID(), null,
                Map.of("tipo_rol", "PREFECTO"),
                "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persona");
    }

    @Test
    void command_sinLaborales_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarPersonalAdminUseCase.Command(
                UUID.randomUUID(),
                Map.of("nombre", "Juan"),
                null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("laborales");
    }

    @Test
    void command_sinTipoRol_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarPersonalAdminUseCase.Command(
                UUID.randomUUID(),
                Map.of("nombre", "Juan", "apellido_paterno", "Pérez"),
                Map.of("area", "Administración"),
                "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_rol");
    }

    @Test
    void command_valido_noLanzaExcepcion() {
        assertThatCode(() -> new RegistrarPersonalAdminUseCase.Command(
                UUID.randomUUID(),
                Map.of("nombre", "María", "apellido_paterno", "García"),
                Map.of("tipo_rol", "SECRETARIA", "area", "Dirección"),
                "admin"))
                .doesNotThrowAnyException();
    }
}
