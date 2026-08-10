package example.org.nightout.storage;

public record StorageFile(String id, String filename, String mimeType, long sizeBytes) {
}
