package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;
import example.org.nightout.exception.StorageException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class S3StorageConfigTest {

    @Test
    void missingAccessKeyFailsFast() {
        AppProperties properties = new AppProperties();
        properties.getS3().setSecretKey("test-secret");

        assertThatThrownBy(() -> new S3StorageConfig().nightoutS3Client(properties))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("NIGHTOUT_S3_ACCESS_KEY");
    }

    @Test
    void s3ConfigurationDisablesChunkedEncodingForRcloneCompatibility() {
        AppProperties.S3 s3 = new AppProperties.S3();
        s3.setPathStyle(true);

        assertThat(S3StorageConfig.s3Configuration(s3).pathStyleAccessEnabled()).isTrue();
        assertThat(S3StorageConfig.s3Configuration(s3).chunkedEncodingEnabled()).isFalse();
    }
}
