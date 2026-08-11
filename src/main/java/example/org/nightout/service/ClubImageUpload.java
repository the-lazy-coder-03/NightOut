package example.org.nightout.service;

import java.time.Instant;

public record ClubImageUpload(String storageFileId, String mimeType, long fileSize, Instant uploadedAt) {
}
