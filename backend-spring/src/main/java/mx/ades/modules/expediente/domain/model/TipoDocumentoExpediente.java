package mx.ades.modules.expediente.domain.model;

import java.util.Set;

/**
 * Tipo de documento que puede formar parte del expediente escolar de un alumno.
 * <p>Valores: CURP, ACTA_NACIMIENTO, CERTIFICADO_PREV, COMPROBANTE_DOMICILIO, FOTOGRAFIA,
 * NSS, CREDENCIAL_ESCOLAR, CONSTANCIA_INSCRIPCION, OTRO.
 * Los cinco primeros ({@code REQUERIDOS}) son obligatorios para completar el expediente.
 * Archivos permitidos: PDF, JPEG, PNG, TIFF, WEBP (máx. 20 MB). Se integra con Paperless-ngx para OCR.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum TipoDocumentoExpediente {
    CURP, ACTA_NACIMIENTO, CERTIFICADO_PREV, COMPROBANTE_DOMICILIO, FOTOGRAFIA,
    NSS, CREDENCIAL_ESCOLAR, CONSTANCIA_INSCRIPCION, OTRO;

    private static final long MAX_BYTES = 2L * 1024 * 1024;  // 2 MB
    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/tiff", "image/webp");

    public static final Set<TipoDocumentoExpediente> REQUERIDOS = Set.of(
            CURP, ACTA_NACIMIENTO, CERTIFICADO_PREV, COMPROBANTE_DOMICILIO, FOTOGRAFIA);

    public static TipoDocumentoExpediente of(String valor) {
        try {
            return valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("tipo_documento inválido: " + valor);
        }
    }

    public static void validarArchivo(String mime, long bytes) {
        if (mime == null || !MIME_PERMITIDOS.contains(mime))
            throw new IllegalArgumentException("MIME no permitido: " + mime +
                    ". Permitidos: " + MIME_PERMITIDOS);
        if (bytes > MAX_BYTES)
            throw new IllegalArgumentException("Archivo demasiado grande (máx 2 MB, recibido " +
                    String.format("%.2f", (double) bytes / (1024 * 1024)) + " MB)");
    }

    public boolean esRequerido() {
        return REQUERIDOS.contains(this);
    }
}
