package mx.ades.modules.portal;

import io.minio.*;
import io.minio.Http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Gestión de archivos en el bucket S3 (Oracle Object Storage) para el portal externo.
 * Comparte el bucket principal ({@code minio.bucket}, ya provisionado y verificado) con
 * MinioService, separando por prefijo de key ("convocatorias/", "portal-imagenes/") —
 * antes usaba 2 buckets propios ("portal-convocatorias"/"portal-imagenes") que nunca se
 * crearon en Oracle; el auto-create con MakeBucketArgs falla ahí con "The region of the
 * bucket must be the same as the region you are sending the request to" (bug real
 * encontrado en la migración de SeaweedFS a Oracle, 2026-07-13).
 * Los documentos NUNCA son públicos — solo mediante presigned URLs (15 min).
 */
@Service
@Slf4j
public class PortalStorageService {

    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024; // 2 MB hard limit
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

    @Value("${minio.bucket:ades-archivos}")
    private String bucket;

    private static final String PREFIX_CONVOCATORIAS = "convocatorias-portal/";
    private static final String PREFIX_IMAGENES = "portal-imagenes/";

    @Value("${portal.assets.dir:/srv/assets/convocatorias}")
    private String assetsDir;

    @Value("${portal.public.base-url:https://ades.setag.mx}")
    private String publicBaseUrl;

    private MinioClient client;

    @PostConstruct
    public void init() {
        try {
            String cleanEndpoint = endpoint.replace("http://", "").replace("https://", "");
            client = MinioClient.builder()
                    .endpoint((secure ? "https://" : "http://") + cleanEndpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            // No se auto-crea el bucket aquí: es el mismo bucket compartido de MinioService
            // (ya provisionado); intentar makeBucket sobre un proveedor real (Oracle) sin el
            // parámetro de región correcto falla — solo se advierte si no existe.
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                log.error("El bucket '{}' no existe — verificar aprovisionamiento.", bucket);
            }
        } catch (Exception e) {
            log.error("Error inicializando cliente S3 del portal: {}", e.getMessage(), e);
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
                    "El archivo supera el límite de 2 MB");
        }
        String mime = archivo.getContentType();
        if (mime == null || !MIME_WHITELIST.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de archivo no permitido. Formatos aceptados: PDF, JPG, PNG, DOC, DOCX");
        }

        String ext = extensionDe(archivo.getOriginalFilename(), mime);
        String key  = String.format("%s%s/%s.%s", PREFIX_CONVOCATORIAS, postulacionId, UUID.randomUUID(), ext);

        try {
            byte[] bytes = archivo.getBytes();
            String sha256 = sha256Hex(bytes);
            try (InputStream is = archivo.getInputStream()) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                    .stream(is, (long) bytes.length, -1L)
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

    private static final Set<String> IMAGE_MIMES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024; // 2 MB

    /**
     * Sube imagen de convocatoria con escritura dual:
     * 1. Filesystem → /srv/assets/convocatorias/ (servido por nginx, URL pública inmutable)
     * 2. Bucket S3 compartido, prefijo "portal-imagenes/" (respaldo, acceso presigned)
     * Retorna la URL pública nginx para guardar en imagen_url.
     */
    public String subirImagenConvocatoria(UUID convocatoriaId, MultipartFile imagen) {
        if (imagen == null || imagen.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen vacía");
        if (imagen.getSize() > MAX_IMAGE_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen supera el límite de 5 MB");
        String mime = imagen.getContentType();
        if (mime == null || !IMAGE_MIMES.contains(mime))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato no permitido. Use JPG, PNG o WebP");

        String ext = extensionDe(imagen.getOriginalFilename(), mime);
        String filename = convocatoriaId + "_" + UUID.randomUUID() + "." + ext;

        byte[] bytes;
        try {
            bytes = imagen.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error leyendo imagen");
        }

        // 1. Escritura primaria: nginx static (URL pública)
        Path dir = Path.of(assetsDir);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);
        } catch (IOException e) {
            log.error("Error guardando imagen de convocatoria en filesystem: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la imagen");
        }

        // 2. Respaldo en el bucket S3 compartido (no bloquea si falla)
        try {
            if (client != null) {
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes)) {
                    client.putObject(PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(PREFIX_IMAGENES + filename)
                            .stream(bais, (long) bytes.length, -1L)
                            .contentType(mime)
                            .build());
                }
                log.info("Imagen respaldada en bucket S3: {}/{}{}", bucket, PREFIX_IMAGENES, filename);
            }
        } catch (Exception e) {
            log.warn("Respaldo S3 fallido (no crítico): {}", e.getMessage());
        }

        return publicBaseUrl + "/assets/convocatorias/" + filename;
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
