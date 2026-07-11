package mx.ades.common;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PiiEncryptionServiceTest {

    private static String randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void cifraYDescifraCorrectamente() {
        PiiEncryptionService svc = new PiiEncryptionService(randomKey());
        String original = "AAAA010101HDFRRL09";
        String cipher = svc.encrypt(original);
        assertNotNull(cipher);
        assertNotEquals(original, cipher);
        assertEquals(original, svc.decrypt(cipher));
    }

    @Test
    void nullYVacioSePreservan() {
        PiiEncryptionService svc = new PiiEncryptionService(randomKey());
        assertNull(svc.encrypt(null));
        assertNull(svc.encrypt(""));
        assertNull(svc.decrypt(null));
        assertNull(svc.decrypt(""));
    }

    @Test
    void cifradoNoEsDeterministico() {
        // IV aleatorio: cifrar el mismo valor dos veces da resultados distintos
        // (protege contra análisis de frecuencia sobre columnas *_encrypted).
        PiiEncryptionService svc = new PiiEncryptionService(randomKey());
        String a = svc.encrypt("juan@example.com");
        String b = svc.encrypt("juan@example.com");
        assertNotEquals(a, b);
        assertEquals("juan@example.com", svc.decrypt(a));
        assertEquals("juan@example.com", svc.decrypt(b));
    }

    @Test
    void descifrarConClaveDistintaFalla() {
        PiiEncryptionService svc1 = new PiiEncryptionService(randomKey());
        PiiEncryptionService svc2 = new PiiEncryptionService(randomKey());
        String cipher = svc1.encrypt("dato sensible");
        assertThrows(IllegalStateException.class, () -> svc2.decrypt(cipher));
    }

    @Test
    void detectaManipulacionDelCiphertext() {
        PiiEncryptionService svc = new PiiEncryptionService(randomKey());
        String cipher = svc.encrypt("dato sensible");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= 0x01; // voltea el último bit del auth tag
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> svc.decrypt(tampered));
    }

    @Test
    void hashEsDeterministicoYNormaliza() {
        PiiEncryptionService svc = new PiiEncryptionService(randomKey());
        assertEquals(svc.hash("juan@example.com"), svc.hash("JUAN@example.com"));
        assertEquals(svc.hash("  abc123  "), svc.hash("ABC123"));
    }

    @Test
    void hashNoEsReversible() {
        PiiEncryptionService svc = new PiiEncryptionService(randomKey());
        String h = svc.hash("AAAA010101HDFRRL09");
        assertNotEquals("AAAA010101HDFRRL09", h);
        assertTrue(h.length() > 0);
    }

    @Test
    void sinClaveConfiguradaFallaExplicitamente() {
        PiiEncryptionService svc = new PiiEncryptionService("");
        assertFalse(svc.isConfigured());
        assertThrows(IllegalStateException.class, () -> svc.encrypt("valor"));
    }

    @Test
    void claveConLongitudInvalidaFallaAlConstruir() {
        String claveCorta = Base64.getEncoder().encodeToString(new byte[16]); // 128 bits, no 256
        assertThrows(IllegalStateException.class, () -> new PiiEncryptionService(claveCorta));
    }
}
