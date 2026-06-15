package mx.ades.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdesIdTest {

    @Test
    void generate_debeProducirUUIDv7Distinto() {
        AdesId a = AdesId.generate();
        AdesId b = AdesId.generate();
        assertNotEquals(a, b);
        assertNotNull(a.value());
    }

    @Test
    void of_UUID_debeEnvolverCorrectamente() {
        UUID uuid = UUID.randomUUID();
        AdesId id = AdesId.of(uuid);
        assertEquals(uuid, id.value());
    }

    @Test
    void of_String_debeParseCorrectamente() {
        String raw = "018f1c2e-3a4b-7000-8000-000000000001";
        AdesId id = AdesId.of(raw);
        assertEquals(raw, id.toString());
    }

    @Test
    void constructor_conNuloDebeLanzarExcepcion() {
        assertThrows(NullPointerException.class, () -> new AdesId(null));
    }
}
