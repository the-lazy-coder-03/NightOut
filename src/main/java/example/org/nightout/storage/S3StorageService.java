package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;
import example.org.nightout.exception.StorageException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
@ConditionalOnProperty(prefix = "nightout", name = "storage-provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    private static final int MAX_STORAGE_FILE_ID_LENGTH = 191;

    private final S3Client s3Client;
    private final String bucket;

    public S3StorageService(AppProperties properties, S3Client s3Client) {
        this.s3Client = s3Client;
        this.bucket = requireBucket(properties.getS3().getBucket());
    }

    @Override
    public StorageFile upload(byte[] content, String filename, String mimeType, String storagePrefix) {
        String key = objectKey(storagePrefix, filename);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(mimeType)
                    .contentLength((long) content.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
            return new StorageFile(key, filename, mimeType, content.length);
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("S3 upload failed for object key " + key + ".", ex);
        }
    }

    @Override
    public StorageResource retrieve(String storageFileId) {
        String key = requireObjectKey(storageFileId);
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())) {
            byte[] body = response.readAllBytes();
            Long contentLength = response.response().contentLength();
            return new StorageResource(new ByteArrayResource(body), contentLength == null ? body.length : contentLength);
        } catch (IOException ex) {
            throw new StorageException("Could not read S3 object " + key + ".", ex);
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("S3 retrieve failed for object key " + key + ".", ex);
        }
    }

    @Override
    public List<StorageFile> list(String storagePrefix) {
        String prefix = normalizePrefix(storagePrefix);
        try {
            ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                    .bucket(bucket);
            if (!prefix.isBlank()) {
                request.prefix(prefix + "/");
            }
            List<StorageFile> files = new ArrayList<>();
            String continuationToken = null;
            do {
                ListObjectsV2Response response = s3Client.listObjectsV2(request.continuationToken(continuationToken).build());
                response.contents().stream()
                        .map(this::toStorageFile)
                        .forEach(files::add);
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null);
            return files;
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("S3 list failed for prefix " + prefix + ".", ex);
        }
    }

    @Override
    public boolean exists(String storageFileId) {
        String key = requireObjectKey(storageFileId);
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new StorageException("S3 head failed for object key " + key + ".", ex);
        } catch (SdkClientException ex) {
            throw new StorageException("S3 head failed for object key " + key + ".", ex);
        }
    }

    @Override
    public void delete(String storageFileId) {
        String key = requireObjectKey(storageFileId);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("S3 delete failed for object key " + key + ".", ex);
        }
    }

    private StorageFile toStorageFile(S3Object object) {
        String key = object.key();
        Long size = object.size();
        return new StorageFile(key, filenameFromKey(key), "application/octet-stream", size == null ? 0 : size);
    }

    private static String objectKey(String storagePrefix, String filename) {
        String safeFilename = requireFilename(filename);
        String prefix = normalizePrefix(storagePrefix);
        String key = prefix.isBlank() ? safeFilename : prefix + "/" + safeFilename;
        return requireObjectKey(key);
    }

    private static String requireObjectKey(String key) {
        String normalized = normalizePrefix(key);
        if (normalized.isBlank()) {
            throw new StorageException("S3 object key is required.");
        }
        if (normalized.length() > MAX_STORAGE_FILE_ID_LENGTH) {
            throw new StorageException("S3 object key is too long for photos.storage_file_id.");
        }
        return normalized;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("//")) {
            throw new StorageException("S3 object keys and prefixes must not contain repeated slashes.");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new StorageException("S3 object keys and prefixes must not contain empty, '.', or '..' segments.");
            }
        }
        return normalized;
    }

    private static String requireFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new StorageException("S3 object filename is required.");
        }
        String normalized = filename.trim().replace('\\', '/');
        if (normalized.contains("/")) {
            throw new StorageException("S3 object filenames must not contain path separators.");
        }
        if (".".equals(normalized) || "..".equals(normalized)) {
            throw new StorageException("S3 object filename is not valid.");
        }
        return normalized;
    }

    private static String requireBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new StorageException("NIGHTOUT_S3_BUCKET is required when using S3 storage.");
        }
        return bucket.trim();
    }

    private static String filenameFromKey(String key) {
        int slashIndex = key.lastIndexOf('/');
        return slashIndex < 0 ? key : key.substring(slashIndex + 1);
    }
}
