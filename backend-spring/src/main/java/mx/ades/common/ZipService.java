package mx.ades.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.expediente.PaperlessService;
import org.springframework.stereotype.Service;

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
 * {@link mx.ades.modules.expediente.PaperlessService} y los empaqueta en el
 * {@link java.io.OutputStream} proporcionado (generalmente el de la respuesta HTTP).
 * Los documentos sin {@code paperless_doc_id} o cuya descarga falla se omiten con
 * advertencia en el log, permitiendo entregas parciales.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZipService {

    private final PaperlessService paperlessService;

    public void compressDocuments(List<Map<String, Object>> documentos, OutputStream outputStream) {
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
}
