package example.org.nightout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "nightout.storage-provider=9drive",
        "nightout.nine-drive.base-url=https://drive.example.test"
})
class NightoutNineDriveContextTest {

    @Test
    void contextLoadsWithNineDriveStorage() {
    }
}
