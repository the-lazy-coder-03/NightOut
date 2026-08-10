package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;
import example.org.nightout.exception.StorageException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "nightout", name = "storage-provider", havingValue = "9drive")
public class NineDriveStorageService implements StorageService {

    private final RestClient restClient;
    private final AppProperties.NineDrive properties;
    private volatile String accessToken;

    public NineDriveStorageService(AppProperties appProperties, RestClient.Builder restClientBuilder) {
        this.properties = appProperties.getNineDrive();
        this.restClient = restClientBuilder.baseUrl(stripTrailingSlash(properties.getBaseUrl())).build();
    }

    @Override
    public StorageFile upload(byte[] content, String filename, String mimeType, String folderId) {
        if (isBlank(properties.getApiKey())) {
            throw new StorageException("NIGHTOUT_9DRIVE_API_KEY is required when using 9Drive storage.");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("sizeBytes", String.valueOf(content.length));
        body.add("fileName", filename);
        body.add("mimeType", mimeType);
        String targetFolderId = firstPresent(folderId, properties.getFolderId());
        if (!isBlank(targetFolderId)) {
            body.add("folderId", targetFolderId);
        }
        body.add("file", new NamedByteArrayResource(content, filename));

        try {
            NineDriveUploadResponse response = restClient.post()
                    .uri("/api/v1/uploads")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(NineDriveUploadResponse.class);
            NineDriveFile file = response == null ? null : response.primaryFile();
            if (file == null || isBlank(file.id())) {
                throw new StorageException("9Drive did not return an uploaded file id.");
            }
            return new StorageFile(file.id(), firstPresent(file.name(), filename), firstPresent(file.mimeType(), mimeType), file.sizeAsLong(content.length));
        } catch (HttpStatusCodeException ex) {
            throw new StorageException("9Drive upload failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public StorageResource retrieve(String storageFileId) {
        byte[] body = withAuthRetry(() -> restClient.get()
                .uri("/files/{id}/download", storageFileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + requireAccessToken())
                .retrieve()
                .body(byte[].class));
        if (body == null) {
            throw new StorageException("9Drive returned an empty download response.");
        }
        return new StorageResource(new ByteArrayResource(body), body.length);
    }

    @Override
    public void delete(String storageFileId) {
        withAuthRetry(() -> {
            restClient.delete()
                    .uri("/files/{id}", storageFileId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + requireAccessToken())
                    .retrieve()
                    .toBodilessEntity();
            restClient.method(HttpMethod.DELETE)
                    .uri("/files/batch/permanent")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + requireAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fileIds", List.of(storageFileId)))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T withAuthRetry(StorageCall<T> call) {
        try {
            return call.execute();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 401) {
                accessToken = null;
                return call.execute();
            }
            throw new StorageException("9Drive request failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    private String requireAccessToken() {
        if (!isBlank(accessToken)) {
            return accessToken;
        }
        synchronized (this) {
            if (!isBlank(accessToken)) {
                return accessToken;
            }
            if (isBlank(properties.getEmail()) || isBlank(properties.getPassword())) {
                throw new StorageException("NIGHTOUT_9DRIVE_EMAIL and NIGHTOUT_9DRIVE_PASSWORD are required for 9Drive downloads and cleanup.");
            }
            NineDriveLoginResponse response = restClient.post()
                    .uri("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", properties.getEmail(), "password", properties.getPassword()))
                    .retrieve()
                    .body(NineDriveLoginResponse.class);
            if (response == null || isBlank(response.accessToken())) {
                throw new StorageException("9Drive login did not return an access token.");
            }
            accessToken = response.accessToken();
            return accessToken;
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String firstPresent(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private interface StorageCall<T> {
        T execute();
    }

    private record NineDriveLoginResponse(String accessToken) {
    }

    private record NineDriveUploadResponse(NineDriveFile file, List<NineDriveFile> files) {
        NineDriveFile primaryFile() {
            if (file != null) {
                return file;
            }
            return files == null || files.isEmpty() ? null : files.getFirst();
        }
    }

    private record NineDriveFile(String id, String name, String mimeType, String sizeBytes) {
        long sizeAsLong(long fallback) {
            if (isBlank(sizeBytes)) {
                return fallback;
            }
            try {
                return Long.parseLong(sizeBytes);
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
