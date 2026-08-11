package example.org.nightout.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.getTimeZone()));
    }

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(AppProperties properties) {
        return new SchemaVersionFlywayMigrationStrategy(properties);
    }

    @Bean
    ThreadPoolTaskExecutor imageOptimizationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("image-opt-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

}
