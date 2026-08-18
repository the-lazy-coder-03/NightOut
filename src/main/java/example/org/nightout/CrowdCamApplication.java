package example.org.nightout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CrowdCamApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrowdCamApplication.class, args);
    }

}
