package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;
import example.org.nightout.exception.StorageException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    S3Client s3Client;

    S3StorageService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getS3().setBucket("nightout");
        service = new S3StorageService(properties, s3Client);
    }

    @Test
    void uploadPutsObjectWithBucketKeyContentTypeAndBytes() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StorageFile uploaded = service.upload(new byte[]{1, 2, 3, 4}, "photo.jpg", "image/jpeg", "clubs/halo/2026-08-10");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("nightout");
        assertThat(request.key()).isEqualTo("clubs/halo/2026-08-10/photo.jpg");
        assertThat(request.contentType()).isEqualTo("image/jpeg");
        assertThat(request.contentLength()).isEqualTo(4);
        assertThat(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes())
                .containsExactly(1, 2, 3, 4);
        assertThat(uploaded.id()).isEqualTo("clubs/halo/2026-08-10/photo.jpg");
        assertThat(uploaded.mimeType()).isEqualTo("image/jpeg");
        assertThat(uploaded.sizeBytes()).isEqualTo(4);
    }

    @Test
    void retrieveGetsObjectByKey() throws Exception {
        byte[] body = new byte[]{5, 6, 7};
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) body.length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(body))));

        StorageResource resource = service.retrieve("clubs/halo/2026-08-10/photo.jpg");

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("nightout");
        assertThat(requestCaptor.getValue().key()).isEqualTo("clubs/halo/2026-08-10/photo.jpg");
        assertThat(resource.contentLength()).isEqualTo(3);
        assertThat(resource.resource().getInputStream().readAllBytes()).containsExactly(5, 6, 7);
    }

    @Test
    void deleteDeletesObjectByKey() {
        service.delete("clubs/halo/2026-08-10/photo.jpg");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("nightout");
        assertThat(requestCaptor.getValue().key()).isEqualTo("clubs/halo/2026-08-10/photo.jpg");
    }

    @Test
    void existsUsesHeadObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        assertThat(service.exists("clubs/halo/2026-08-10/photo.jpg")).isTrue();

        ArgumentCaptor<HeadObjectRequest> requestCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("nightout");
        assertThat(requestCaptor.getValue().key()).isEqualTo("clubs/halo/2026-08-10/photo.jpg");
    }

    @Test
    void existsReturnsFalseForMissingObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder()
                .statusCode(404)
                .message("not found")
                .build());

        assertThat(service.exists("clubs/halo/2026-08-10/missing.jpg")).isFalse();
    }

    @Test
    void listUsesListObjectsV2WithPrefixAndPagination() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder()
                        .key("clubs/halo/2026-08-10/photo.jpg")
                        .size(4L)
                        .build())
                .nextContinuationToken("next-page")
                .build(), ListObjectsV2Response.builder()
                .contents(S3Object.builder()
                        .key("clubs/halo/2026-08-10/second.jpg")
                        .size(5L)
                        .build())
                .build());

        assertThat(service.list("clubs/halo/2026-08-10"))
                .extracting(StorageFile::id)
                .containsExactly("clubs/halo/2026-08-10/photo.jpg", "clubs/halo/2026-08-10/second.jpg");

        ArgumentCaptor<ListObjectsV2Request> requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, org.mockito.Mockito.times(2)).listObjectsV2(requestCaptor.capture());
        List<ListObjectsV2Request> requests = requestCaptor.getAllValues();
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.bucket()).isEqualTo("nightout");
            assertThat(request.prefix()).isEqualTo("clubs/halo/2026-08-10/");
        });
        assertThat(requests.getFirst().continuationToken()).isNull();
        assertThat(requests.get(1).continuationToken()).isEqualTo("next-page");
    }

    @Test
    void storageFailuresBecomeStorageException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(S3Exception.builder()
                .statusCode(500)
                .message("rclone failed")
                .build());

        assertThatThrownBy(() -> service.upload(new byte[]{1}, "photo.jpg", "image/jpeg", "clubs/halo"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("S3 upload failed");
    }

    @Test
    void invalidKeySegmentsAreRejectedBeforeCallingS3() {
        assertThatThrownBy(() -> service.upload(new byte[]{1}, "photo.jpg", "image/jpeg", "clubs/../halo"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("must not contain");

        verifyNoInteractions(s3Client);
    }
}
