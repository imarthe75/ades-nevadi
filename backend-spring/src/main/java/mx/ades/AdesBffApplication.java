package mx.ades;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdesBffApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AdesBffApplication.class);
        app.addInitializers(new mx.ades.config.VaultInitializer());
        app.run(args);
    }
}
