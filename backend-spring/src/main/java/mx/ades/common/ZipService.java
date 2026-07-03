package mx.ades.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.expediente.PaperlessService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servicio de compresión que agrupa documentos de expediente en un único archivo ZIP
 * para descarga masiva desde el frontend de ADES.
 * <p>
 * Recupera el contenido binario de cada documento desde Paperless-ngx a través de
 * {@link mx.ades.modules.expediente.PaperlessService}, opcionalmente lo comprime vía
 * Stirling-PDF (FASE 34 — "expedientes consolidados vía Stirling-PDF") y lo empaqueta
 * en el {@link java.io.OutputStream} proporcionado (generalmente el de la respuesta HTTP).
 * Los documentos sin {@code paperless_doc_id} o cuya descarga falla se omiten con
 * advertencia en el log, permitiendo entregas parciales. Si Stirling-PDF no está
 * disponible o la compresión falla, se usa el documento original sin comprimir —
 * la compresión es una optimización de tamaño, nunca bloqueante.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZipService {

    private static final String STIRLING_COMPRIMIR_URL = "http://ades-api:8000/api/v1/pdf/comprimir";

    private final PaperlessService paperlessService;
    private final RestClient restClient = RestClient.create();

    public void compressDocuments(List<Map<String, Object>> documentos, OutputStream outputStream) {
        compressDocuments(documentos, outputStream, null);
    }

    public void compressDocuments(List<Map<String, Object>> documentos, OutputStream outputStream, String bearerToken) {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            for (Map<String, Object> doc : documentos) {
                Integer paperlessDocId = (Integer) doc.get("paperless_doc_id");
                String nombreArchivo = (String) doc.get("nombre_archivo");
                String tipoDoc = (String) doc.get("tipo_documento");

                if (paperlessDocId == null) {
                    log.warn("El documento {} ({}) no tiene ID de Paperless, se omite.", nombreArchivo, tipoDoc);
                    continue;
                }

                if (nombreArchivo == null || nombreArchivo.isBlank()) {
                    nombreArchivo = tipoDoc + "_" + paperlessDocId + ".pdf";
                }

                byte[] content = paperlessService.descargarDocumento(paperlessDocId);
                if (content == null) {
                    log.warn("No se pudo descargar el documento {} de Paperless.", paperlessDocId);
                    continue;
                }

                if (nombreArchivo.toLowerCase().endsWith(".pdf")) {
                    content = comprimirSiEsPosible(content, nombreArchivo, bearerToken);
                }

                ZipEntry entry = new ZipEntry(nombreArchivo);
                zos.putNextEntry(entry);
                zos.write(content);
                zos.closeEntry();
            }
            zos.finish();
        } catch (Exception e) {
            log.error("Error al generar el archivo ZIP: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar el archivo ZIP", e);
        }
    }

    /**
     * Comprime un PDF vía el proxy Stirling-PDF de FastAPI (nivel 3/5, balance
     * tamaño/calidad). Devuelve el contenido original si Stirling no está
     * disponible o la llamada falla — nunca bloquea la generación del ZIP.
     */
    private byte[] comprimirSiEsPosible(byte[] original, String nombreArchivo, String bearerToken) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("pdf", original)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"pdf\"; filename=\"" + nombreArchivo + "\"");
            builder.part("nivel", "3");

            RestClient.RequestBodySpec request = restClient.post()
                    .uri(STIRLING_COMPRIMIR_URL)
                    .contentType(MediaType.MULTIPART_FORM_DATA);
            if (bearerToken != null && !bearerToken.isBlank()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            byte[] comprimido = request
                    .body(builder.build())
                    .retrieve()
                    .body(byte[].class);

            if (comprimido != null && comprimido.length > 0) {
                return comprimido;
            }
        } catch (Exception e) {
            log.debug("No se pudo comprimir {} vía Stirling-PDF, se usa el original: {}", nombreArchivo, e.getMessage());
        }
        return original;
    }
}
