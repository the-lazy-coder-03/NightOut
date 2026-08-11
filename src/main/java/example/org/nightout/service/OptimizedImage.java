package example.org.nightout.service;

public record OptimizedImage(byte[] content, String mimeType, String extension, int width, int height) {
}
