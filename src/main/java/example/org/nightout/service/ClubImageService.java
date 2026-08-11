package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.Club;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.exception.StorageException;
import example.org.nightout.storage.StorageFile;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClubImageService {

    private static final Logger log = LoggerFactory.getLogger(ClubImageService.class);

    private final StorageService storageService;
    private final ImageUploadValidator imageUploadValidator;
    private final ImageOptimizer imageOptimizer;
    private final AppProperties properties;
    private final Clock clock;

    public ClubImageService(StorageService storageService, ImageUploadValidator imageUploadValidator, ImageOptimizer imageOptimizer, AppProperties properties, Clock clock) {
        this.storageService = storageService;
        this.imageUploadValidator = imageUploadValidator;
        this.imageOptimizer = imageOptimizer;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<ClubImageUpload> upload(MultipartFile file, String clubSlug) {
        if (file == null || file.isEmpty()) {
            return Optional.empty();
        }

        ValidatedImage validated = imageUploadValidator.validate(file, properties.getMaxUploadBytes());
        try {
            OptimizedImage optimized = imageOptimizer.optimize(
                    validated.content(),
                    properties.getImageOptimization().getMaxDimension(),
                    properties.getImageOptimization().getJpegQuality()
            );
            StorageFile stored = storageService.upload(
                    optimized.content(),
                    "club-image-" + UUID.randomUUID() + "." + optimized.extension(),
                    optimized.mimeType(),
                    storagePrefix(clubSlug)
            );
            return Optional.of(new ClubImageUpload(stored.id(), stored.mimeType(), stored.sizeBytes(), Instant.now(clock)));
        } catch (StorageException ex) {
            throw new BusinessRuleException("Could not process the club image. Please upload a valid JPEG, PNG, or WebP file.");
        }
    }

    public StorageResource retrieve(Club club) {
        if (club == null || !club.hasUploadedImage()) {
            throw new ResourceNotFoundException("Club image not found.");
        }
        return storageService.retrieve(club.getImageStorageFileId());
    }

    public void deleteQuietly(String storageFileId) {
        if (!StringUtils.hasText(storageFileId)) {
            return;
        }
        try {
            storageService.delete(storageFileId);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete replaced club image {}.", storageFileId, ex);
        }
    }

    private static String storagePrefix(String clubSlug) {
        String safeSlug = StringUtils.hasText(clubSlug) ? clubSlug.trim() : "club";
        return "clubs/" + safeSlug + "/club-image";
    }
}
