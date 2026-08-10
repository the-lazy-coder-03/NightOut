package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;
import example.org.nightout.exception.StorageException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "nightout", name = "storage-provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path rootPath;

    public LocalStorageService(AppProperties properties) {
        this.rootPath = Path.of(properties.getLocalStoragePath()).toAbsolutePath().normalize();
    }

    @Override
    public StorageFile upload(byte[] content, String filename, String mimeType, String folderId) {
        try {
            Files.createDirectories(rootPath);
            String id = UUID.randomUUID().toString();
            Files.write(rootPath.resolve(id), content);
            return new StorageFile(id, filename, mimeType, content.length);
        } catch (IOException ex) {
            throw new StorageException("Could not store the uploaded file.", ex);
        }
    }

    @Override
    public StorageResource retrieve(String storageFileId) {
        Path path = rootPath.resolve(storageFileId).normalize();
        if (!path.startsWith(rootPath) || !Files.exists(path)) {
            throw new StorageException("Stored file was not found.");
        }
        try {
            return new StorageResource(new FileSystemResource(path), Files.size(path));
        } catch (IOException ex) {
            throw new StorageException("Could not read the stored file.", ex);
        }
    }

    @Override
    public void delete(String storageFileId) {
        try {
            Files.deleteIfExists(rootPath.resolve(storageFileId).normalize());
        } catch (IOException ex) {
            throw new StorageException("Could not delete the stored file.", ex);
        }
    }
}
