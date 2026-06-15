package mx.ades.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class VaultInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(VaultInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String vaultAddr = System.getenv("VAULT_ADDR");
        if (vaultAddr == null || vaultAddr.isEmpty()) {
            vaultAddr = "http://ades-vault:8200"; // Default address inside docker network
        }

        String vaultToken = System.getenv("VAULT_TOKEN");
        if (vaultToken == null || vaultToken.isEmpty()) {
            Path tokenPath = Paths.get("/vault/init/root_token.txt");
            if (Files.exists(tokenPath)) {
                try {
                    vaultToken = Files.readString(tokenPath).trim();
                } catch (IOException e) {
                    logger.error("Error al leer el token de Vault desde /vault/init/root_token.txt: {}", e.getMessage());
                }
            }
        }

        if (vaultToken == null || vaultToken.isEmpty()) {
            logger.warn("VAULT_TOKEN no encontrado. Se omitirá la configuración de Vault.");
            return;
        }

        try {
            logger.info("Conectando a HashiCorp Vault en {} para cargar configuraciones...", vaultAddr);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + "/v1/secret/data/ades"))
                    .header("X-Vault-Token", vaultToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());
                JsonNode dataNode = rootNode.path("data").path("data");

                if (dataNode.isObject()) {
                    Map<String, Object> vaultProperties = new HashMap<>();

                    // Parse y mapeo de DATABASE_URL_SYNC o DATABASE_URL
                    String dbSyncUrl = dataNode.path("DATABASE_URL_SYNC").asText();
                    if (dbSyncUrl != null && !dbSyncUrl.isEmpty()) {
                        try {
                            // Formato esperado: postgresql://username:password@host:port/database
                            URI dbUri = URI.create(dbSyncUrl.replace("postgresql://", "http://"));
                            String userInfo = dbUri.getUserInfo();
                            String username = "";
                            String password = "";
                            if (userInfo != null && userInfo.contains(":")) {
                                String[] parts = userInfo.split(":", 2);
                                username = parts[0];
                                password = parts[1];
                            }
                            String host = dbUri.getHost();
                            int port = dbUri.getPort();
                            String path = dbUri.getPath();

                            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                            vaultProperties.put("SPRING_DATASOURCE_URL", jdbcUrl);
                            vaultProperties.put("SPRING_DATASOURCE_USERNAME", username);
                            vaultProperties.put("SPRING_DATASOURCE_PASSWORD", password);
                            logger.info("Cargada configuración de SPRING_DATASOURCE_URL, USERNAME, PASSWORD desde Vault.");
                        } catch (Exception e) {
                            logger.error("Error al parsear DATABASE_URL_SYNC: {}", e.getMessage());
                            String jdbcUrl = dbSyncUrl.replace("postgresql://", "jdbc:postgresql://");
                            vaultProperties.put("SPRING_DATASOURCE_URL", jdbcUrl);
                        }
                    }

                    // Parse y mapeo de VALKEY_URL
                    String valkeyUrl = dataNode.path("VALKEY_URL").asText();
                    if (valkeyUrl != null && !valkeyUrl.isEmpty()) {
                        try {
                            // Formato esperado: redis://:password@host:port/db
                            URI redisUri = URI.create(valkeyUrl.replace("redis://", "http://"));
                            String host = redisUri.getHost();
                            int port = redisUri.getPort();
                            String userInfo = redisUri.getUserInfo();
                            String password = "";
                            if (userInfo != null && userInfo.contains(":")) {
                                password = userInfo.split(":")[1];
                            }

                            vaultProperties.put("SPRING_DATA_REDIS_HOST", host);
                            vaultProperties.put("SPRING_DATA_REDIS_PORT", String.valueOf(port));
                            vaultProperties.put("SPRING_DATA_REDIS_PASSWORD", password);
                            logger.info("Cargada configuración de Redis (Valkey) desde Vault.");
                        } catch (Exception e) {
                            logger.error("Error al parsear VALKEY_URL de Vault: {}", e.getMessage());
                        }
                    }

                    // Parse y mapeo de MinIO/SeaweedFS
                    String minioEndpoint = dataNode.path("MINIO_ENDPOINT").asText();
                    String minioAccessKey = dataNode.path("MINIO_ACCESS_KEY").asText();
                    String minioSecretKey = dataNode.path("MINIO_SECRET_KEY").asText();

                    if (minioEndpoint != null && !minioEndpoint.isEmpty()) {
                        vaultProperties.put("MINIO_ENDPOINT", minioEndpoint);
                    }
                    if (minioAccessKey != null && !minioAccessKey.isEmpty()) {
                        vaultProperties.put("MINIO_ROOT_USER", minioAccessKey);
                    }
                    if (minioSecretKey != null && !minioSecretKey.isEmpty()) {
                        vaultProperties.put("MINIO_ROOT_PASSWORD", minioSecretKey);
                    }

                    // Parse y mapeo de OIDC_CLIENT_SECRET
                    String oidcClientSecret = dataNode.path("OIDC_CLIENT_SECRET").asText();
                    if (oidcClientSecret != null && !oidcClientSecret.isEmpty()) {
                        vaultProperties.put("OIDC_CLIENT_SECRET", oidcClientSecret);
                        logger.info("Cargado OIDC_CLIENT_SECRET desde Vault.");
                    }

                    if (!vaultProperties.isEmpty()) {
                        MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
                        propertySources.addFirst(new MapPropertySource("vaultProperties", vaultProperties));
                        logger.info("Secretos de Vault inyectados exitosamente en el entorno de Spring.");
                    }
                }
            } else {
                logger.error("Fallo al obtener secretos de Vault. Status code: {}", response.statusCode());
            }

        } catch (Exception e) {
            logger.error("Error durante la inicialización de secretos con Vault: {}", e.getMessage(), e);
        }
    }
}
