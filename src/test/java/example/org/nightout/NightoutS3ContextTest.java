package example.org.nightout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "nightout.storage-provider=s3",
        "nightout.s3.endpoint=http://127.0.0.1:8080",
        "nightout.s3.bucket=nightout",
        "nightout.s3.region=us-east-1",
        "nightout.s3.access-key=test-access-key",
        "nightout.s3.secret-key=test-secret-key",
        "nightout.s3.path-style=true"
})
class NightoutS3ContextTest {

    @Test
    void contextLoadsWithS3Storage() {
    }
}
