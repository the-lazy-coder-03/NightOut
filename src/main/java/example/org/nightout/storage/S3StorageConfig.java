package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;
import example.org.nightout.exception.StorageException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@ConditionalOnProperty(prefix = "nightout", name = "storage-provider", havingValue = "s3")
class S3StorageConfig {

    @Bean
    S3Client nightoutS3Client(AppProperties properties) {
        AppProperties.S3 s3 = properties.getS3();
        return S3Client.builder()
                .endpointOverride(URI.create(requireSetting("NIGHTOUT_S3_ENDPOINT", s3.getEndpoint())))
                .region(Region.of(requireSetting("NIGHTOUT_S3_REGION", s3.getRegion())))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        requireSetting("NIGHTOUT_S3_ACCESS_KEY", s3.getAccessKey()),
                        requireSetting("NIGHTOUT_S3_SECRET_KEY", s3.getSecretKey()))))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(s3Configuration(s3))
                .build();
    }

    static S3Configuration s3Configuration(AppProperties.S3 s3) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(s3.isPathStyle())
                .chunkedEncodingEnabled(false)
                .build();
    }

    private static String requireSetting(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new StorageException(name + " is required when NIGHTOUT_STORAGE_PROVIDER=s3.");
        }
        return value.trim();
    }
}
