package mx.ades.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado en reposo de PII (CURP, RFC, teléfono, email personal) para
 * cumplimiento LFPDPPP — datos de menores presentes en {@code ades_personas}.
 *
 * <p>Dos primitivas distintas para dos propósitos distintos:
 * <ul>
 *   <li>{@link #encrypt}/{@link #decrypt} — AES-256-GCM, reversible. El
 *       resultado incluye el IV y el authentication tag, por lo que el valor
 *       cifrado es autocontenido (una sola columna {@code *_encrypted}).
 *       GCM detecta manipulación: si el ciphertext fue alterado, decrypt lanza
 *       excepción en vez de devolver basura silenciosamente.</li>
 *   <li>{@link #hash} — HMAC-SHA256, determinístico y NO reversible. Permite
 *       búsquedas exactas indexadas ({@code WHERE curp_hash = ?}) sin poder
 *       recuperar el valor original a partir del hash — GCM es no-determinístico
 *       (IV aleatorio) así que el ciphertext no sirve para eso.</li>
 * </ul>
 *
 * <p>La clave viene de {@code ades.pii.encryption-key} (256 bits, base64) —
 * ver {@code PII_ENCRYPTION_KEY} en docker-compose/.env. Perder esta clave
 * hace irrecuperables los valores ya cifrados; rotarla requiere desencriptar
 * primero con la clave anterior.
 */
@Service
public class PiiEncryptionService {

    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();
    private final boolean configured;

    public PiiEncryptionService(@Value("${ades.pii.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            // Permite arrancar el contexto (tests, entornos sin PII_ENCRYPTION_KEY
            // configurada aún) sin reventar; encrypt/decrypt fallan explícitamente
            // si se invocan sin clave configurada.
            this.aesKey = null;
            this.hmacKey = null;
            this.configured = false;
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "PII_ENCRYPTION_KEY debe ser una clave de 256 bits (32 bytes) en base64, se recibieron " + keyBytes.length + " bytes.");
        }
        this.aesKey = new SecretKeySpec(keyBytes, "AES");
        // Deriva la clave HMAC de la misma clave base con dominio distinto, para no
        // gestionar dos secretos separados en .env.
        this.hmacKey = new SecretKeySpec(hmacSha256(keyBytes, "ades-pii-hash-key-v1".getBytes(StandardCharsets.UTF_8)), HMAC_ALGO);
        this.configured = true;
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Cifra un valor en texto plano; null/blank se preserva como null. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        requireConfigured();
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error cifrando valor PII", e);
        }
    }

    /** Descifra un valor producido por {@link #encrypt}; null se preserva como null. */
    public String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        requireConfigured();
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length < GCM_IV_BYTES) {
                throw new IllegalArgumentException("Valor cifrado con formato inválido (demasiado corto).");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(raw, 0, iv, 0, GCM_IV_BYTES);
            byte[] ciphertext = new byte[raw.length - GCM_IV_BYTES];
            System.arraycopy(raw, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error descifrando valor PII (clave incorrecta o dato manipulado)", e);
        }
    }

    /**
     * Hash determinístico para búsquedas exactas indexadas. Normaliza a
     * mayúsculas+trim antes de hashear para que "juan@x.com" y "JUAN@X.COM"
     * (o "  abc123  ") produzcan el mismo hash — igual criterio que usan los
     * índices UNIQUE existentes sobre curp/rfc/email en texto plano.
     */
    public String hash(String value) {
        if (value == null || value.isEmpty()) return null;
        requireConfigured();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(hmacKey);
            byte[] out = mac.doFinal(value.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error calculando hash PII", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error derivando clave HMAC", e);
        }
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY no está configurada — cifrado PII no disponible.");
        }
    }
}
