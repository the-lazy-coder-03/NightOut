package example.org.nightout.service;

public record ValidatedImage(byte[] content, String originalFilename, String mimeType, String extension) {
}
