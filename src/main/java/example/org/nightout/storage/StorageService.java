package example.org.nightout.storage;

public interface StorageService {
    StorageFile upload(byte[] content, String filename, String mimeType, String folderId);

    StorageResource retrieve(String storageFileId);

    void delete(String storageFileId);
}
