package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class NineDriveStorageServiceTest {

    @Test
    void uploadUsesFilesMetaAndMatchingFileField() {
        AppProperties properties = new AppProperties();
        properties.getNineDrive().setBaseUrl("https://drive.example.test");
        properties.getNineDrive().setApiKey("test-api-key");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NineDriveStorageService service = new NineDriveStorageService(properties, builder);

        server.expect(requestTo("https://drive.example.test/api/v1/uploads"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(allOf(
                        containsString("name=\"filesMeta\""),
                        containsString("\"fieldName\":\"file-0\""),
                        containsString("\"fileName\":\"halo.jpg\""),
                        containsString("\"mimeType\":\"image/jpeg\""),
                        containsString("\"sizeBytes\":\"4\""),
                        containsString("name=\"file-0\"; filename=\"halo.jpg\""),
                        containsString("name=\"folderId\""),
                        containsString("club-folder"),
                        not(containsString("name=\"fileName\"")),
                        not(containsString("name=\"mimeType\"")),
                        not(containsString("name=\"sizeBytes\""))
                )))
                .andRespond(withSuccess("""
                        {"files":[{"id":"file-id","name":"halo.jpg","mimeType":"image/jpeg","sizeBytes":"4"}]}
                        """, MediaType.APPLICATION_JSON));

        StorageFile uploaded = service.upload(new byte[]{1, 2, 3, 4}, "halo.jpg", "image/jpeg", "club-folder");

        assertThat(uploaded.id()).isEqualTo("file-id");
        assertThat(uploaded.filename()).isEqualTo("halo.jpg");
        assertThat(uploaded.mimeType()).isEqualTo("image/jpeg");
        assertThat(uploaded.sizeBytes()).isEqualTo(4);
        server.verify();
    }
}
