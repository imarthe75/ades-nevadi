package mx.ades.modules.contactos;

import mx.ades.modules.contactos.domain.port.in.ActualizarContactoUseCase;
import mx.ades.modules.contactos.domain.port.in.RegistrarContactoUseCase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ContactosDomainTest {

    @Test
    void registrar_estudianteIdNull_throwsIAE() {
        assertThatThrownBy(() -> new RegistrarContactoUseCase.Command(
            null, "Juan Pérez", "PADRE", "5551234567", null,
            false, false, true, null, null, null, null, "admin"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("estudianteId");
    }

    @Test
    void registrar_nacionalidadNull_defaultsMexicana() {
        var cmd = new RegistrarContactoUseCase.Command(
            UUID.randomUUID(), "Juan Pérez", "PADRE", "5551234567", null,
            false, false, true, null, null, null, null, "admin"
        );
        assertThat(cmd.nacionalidad()).isEqualTo("Mexicana");
    }

    @Test
    void registrar_nacionalidadBlank_defaultsMexicana() {
        var cmd = new RegistrarContactoUseCase.Command(
            UUID.randomUUID(), "Ana López", "MADRE", "5559876543", null,
            true, true, true, null, null, null, "  ", "admin"
        );
        assertThat(cmd.nacionalidad()).isEqualTo("Mexicana");
    }

    @Test
    void registrar_conNacionalidadExplicita_seMantiene() {
        var cmd = new RegistrarContactoUseCase.Command(
            UUID.randomUUID(), "María García", "TUTORA", "5551111111", null,
            true, false, true, null, null, null, "Estadounidense", "admin"
        );
        assertThat(cmd.nacionalidad()).isEqualTo("Estadounidense");
    }

    @Test
    void actualizar_contactoIdNull_throwsIAE() {
        assertThatThrownBy(() -> new ActualizarContactoUseCase.Command(
            null, "Juan Pérez", null, null, null,
            null, null, null, null, null, null, null, null, "admin"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("contactoId");
    }

    @Test
    void actualizar_contactoIdValido_ok() {
        var cmd = new ActualizarContactoUseCase.Command(
            UUID.randomUUID(), "Juan Pérez", "PADRE", null, null,
            null, null, null, null, null, null, null, 3, "admin"
        );
        assertThat(cmd.contactoId()).isNotNull();
        assertThat(cmd.rowVersion()).isEqualTo(3);
    }
}
