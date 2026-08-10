package example.org.nightout.storage;

import example.org.nightout.config.AppProperties;
import example.org.nightout.exception.StorageException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(prefix = "nightout", name = "storage-provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path rootPath;

    public LocalStorageService(AppProperties properties) {
        this.rootPath = Path.of(properties.getLocalStoragePath()).toAbsolutePath().normalize();
    }

    @Override
    public StorageFile upload(byte[] content, String filename, String mimeType, String storagePrefix) {
        String id = objectKey(storagePrefix, filename);
        try {
            Path target = resolveStoredPath(id);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return new StorageFile(id, filename, mimeType, content.length);
        } catch (IOException ex) {
            throw new StorageException("Could not store the uploaded file.", ex);
        }
    }

    @Override
    public StorageResource retrieve(String storageFileId) {
        Path path = resolveStoredPath(storageFileId);
        if (!Files.exists(path)) {
            throw new StorageException("Stored file was not found.");
        }
        try {
            return new StorageResource(new FileSystemResource(path), Files.size(path));
        } catch (IOException ex) {
            throw new StorageException("Could not read the stored file.", ex);
        }
    }

    @Override
    public List<StorageFile> list(String storagePrefix) {
        Path listRoot = storagePrefix == null || storagePrefix.isBlank()
                ? rootPath
                : resolveStoredPath(normalizeStoragePath(storagePrefix));
        if (!Files.exists(listRoot)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(listRoot)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(this::localStorageFile)
                    .toList();
        } catch (IOException ex) {
            throw new StorageException("Could not list stored files.", ex);
        }
    }

    @Override
    public boolean exists(String storageFileId) {
        return Files.exists(resolveStoredPath(storageFileId));
    }

    @Override
    public void delete(String storageFileId) {
        try {
            Files.deleteIfExists(resolveStoredPath(storageFileId));
        } catch (IOException ex) {
            throw new StorageException("Could not delete the stored file.", ex);
        }
    }

    private StorageFile localStorageFile(Path path) {
        try {
            String id = rootPath.relativize(path).toString().replace('\\', '/');
            return new StorageFile(id, path.getFileName().toString(), "application/octet-stream", Files.size(path));
        } catch (IOException ex) {
            throw new StorageException("Could not inspect a stored file.", ex);
        }
    }

    private Path resolveStoredPath(String storageFileId) {
        String id = normalizeStoragePath(storageFileId);
        Path path = rootPath.resolve(id).normalize();
        if (!path.startsWith(rootPath)) {
            throw new StorageException("Stored file path is not valid.");
        }
        return path;
    }

    private static String objectKey(String storagePrefix, String filename) {
        String safeFilename = normalizeFilename(filename);
        String prefix = normalizeStoragePath(storagePrefix);
        return prefix.isBlank() ? safeFilename : prefix + "/" + safeFilename;
    }

    private static String normalizeStoragePath(String value) {
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
            throw new StorageException("Stored file paths must not contain repeated slashes.");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new StorageException("Stored file paths must not contain empty, '.', or '..' segments.");
            }
        }
        return normalized;
    }

    private static String normalizeFilename(String filename) {
        String normalized = normalizeStoragePath(filename);
        if (normalized.isBlank() || normalized.contains("/")) {
            throw new StorageException("Stored filenames must not contain path separators.");
        }
        return normalized;
    }
}
