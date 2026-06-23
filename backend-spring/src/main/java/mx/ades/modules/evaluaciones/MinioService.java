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

import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

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
                        .stream(is, file.getSize(), -1)
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
