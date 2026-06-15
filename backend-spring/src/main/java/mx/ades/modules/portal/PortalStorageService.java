package mx.ades.modules.portal;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Gestión de archivos en MinIO para el portal externo.
 * Bucket dedicado: portal-convocatorias.
 * Los documentos NUNCA son públicos — solo mediante presigned URLs (15 min).
 */
@Service
@Slf4j
public class PortalStorageService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB hard limit
    private static final Set<String> MIME_WHITELIST = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Value("${minio.endpoint:ades-minio:9000}")
    private String endpoint;

    @Value("${minio.access-key:ades_minio}")
    private String accessKey;

    @Value("${minio.secret-key:ades_minio_secret}")
    private String secretKey;

    @Value("${minio.secure:false}")
    private boolean secure;

    @Value("${portal.minio.bucket:portal-convocatorias}")
    private String bucket;

    private MinioClient client;

    @PostConstruct
    public void init() {
        try {
            String cleanEndpoint = endpoint.replace("http://", "").replace("https://", "");
            client = MinioClient.builder()
                    .endpoint((secure ? "https://" : "http://") + cleanEndpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket creado: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Error inicializando MinIO portal: {}", e.getMessage(), e);
        }
    }

    /**
     * Sube un archivo y devuelve la ruta interna MinIO.
     * Valida MIME type y tamaño antes de subir.
     */
    public UploadResult subir(UUID postulacionId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo vacío");
        }
        if (archivo.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo supera el límite de 10 MB");
        }
        String mime = archivo.getContentType();
        if (mime == null || !MIME_WHITELIST.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de archivo no permitido. Formatos aceptados: PDF, JPG, PNG, DOC, DOCX");
        }

        String ext = extensionDe(archivo.getOriginalFilename(), mime);
        String key  = String.format("%s/%s.%s", postulacionId, UUID.randomUUID(), ext);

        try {
            byte[] bytes = archivo.getBytes();
            String sha256 = sha256Hex(bytes);
            try (InputStream is = archivo.getInputStream()) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(is, bytes.length, -1)
                        .contentType(mime)
                        .build());
            }
            return new UploadResult(key, sha256, archivo.getOriginalFilename(), mime, bytes.length);
        } catch (Exception e) {
            log.error("Error subiendo archivo portal a MinIO: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al almacenar el archivo");
        }
    }

    /** Genera URL de descarga temporal (15 minutos). */
    public String presignedUrl(String rutaMinio) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(rutaMinio)
                    .method(Method.GET)
                    .expiry(15, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Error generando presigned URL: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error generando URL de descarga");
        }
    }

    /** Elimina un archivo del bucket. */
    public void eliminar(String rutaMinio) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(rutaMinio).build());
        } catch (Exception e) {
            log.warn("Error eliminando archivo MinIO {}: {}", rutaMinio, e.getMessage());
        }
    }

    private String extensionDe(String nombre, String mime) {
        if (nombre != null && nombre.contains(".")) {
            return nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (mime) {
            case "application/pdf" -> "pdf";
            case "image/jpeg"      -> "jpg";
            case "image/png"       -> "png";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            default -> "bin";
        };
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            return null;
        }
    }

    public record UploadResult(String ruta, String sha256, String nombreOriginal, String mime, long tamanoBytes) {}
}
