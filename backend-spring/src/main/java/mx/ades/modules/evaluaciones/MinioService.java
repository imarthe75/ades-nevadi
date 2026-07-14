package mx.ades.modules.evaluaciones;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024; // 50 MB — entregas de tarea (PDF, docs, imágenes)

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    @Value("${minio.endpoint:ades-minio:9000}")
    private String endpoint;

    @Value("${minio.access-key:ades_minio}")
    private String accessKey;

    @Value("${minio.secret-key:ades_minio_secret}")
    private String secretKey;

    @Value("${minio.secure:false}")
    private boolean secure;

    @Value("${minio.bucket:tareas-entregas}")
    private String bucketName;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        try {
            // Clean protocol prefix if present (MinioClient expects host:port or similar if no HTTP scheme is handled, or standard URL)
            String cleanEndpoint = endpoint.replace("http://", "").replace("https://", "");
            
            minioClient = MinioClient.builder()
                    .endpoint((secure ? "https://" : "http://") + cleanEndpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client: {}", e.getMessage(), e);
        }
    }

    public String uploadFile(UUID tareaId, UUID estudianteId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El archivo supera el límite de 50MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Tipo de archivo no permitido: " + contentType);
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "bin";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            }
            
            String key = String.format("%s/%s/%s.%s", tareaId, estudianteId, UUID.randomUUID(), extension);
            
            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(key)
                    .stream(is, file.getSize(), -1L)
                        .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                        .build());
            }

            return String.format("minio://%s/%s", bucketName, key);
        } catch (Exception e) {
            log.error("Error uploading file to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Error subiendo archivo a MinIO: " + e.getMessage(), e);
        }
    }

    public byte[] downloadFile(String minioUrl) {
        if (minioUrl == null || !minioUrl.startsWith("minio://")) {
            return null;
        }
        try {
            String path = minioUrl.substring("minio://".length());
            int slash = path.indexOf('/');
            if (slash == -1) return null;
            String bucket = path.substring(0, slash);
            String objectKey = path.substring(slash + 1);

            try (InputStream is = minioClient.getObject(io.minio.GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build())) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.error("Error downloading file from MinIO: {}", e.getMessage(), e);
            return null;
        }
    }
}
