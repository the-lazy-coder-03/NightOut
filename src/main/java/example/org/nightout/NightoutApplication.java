package example.org.nightout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NightoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(NightoutApplication.class, args);
    }

}
