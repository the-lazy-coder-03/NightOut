package example.org.nightout.storage;

import java.util.List;

public interface StorageService {
    StorageFile upload(byte[] content, String filename, String mimeType, String storagePrefix);

    StorageResource retrieve(String storageFileId);

    List<StorageFile> list(String storagePrefix);

    boolean exists(String storageFileId);

    void delete(String storageFileId);
}
