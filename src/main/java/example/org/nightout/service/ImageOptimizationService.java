package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoOptimizationStatus;
import example.org.nightout.exception.StorageException;
import example.org.nightout.repository.PhotoRepository;
import example.org.nightout.storage.StorageFile;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImageOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizationService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final ImageOptimizer imageOptimizer;
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ThreadPoolTaskExecutor taskExecutor;

    public ImageOptimizationService(
            PhotoRepository photoRepository,
            StorageService storageService,
            ImageOptimizer imageOptimizer,
            AppProperties properties,
            Clock clock,
            TransactionTemplate transactionTemplate,
            @Qualifier("imageOptimizationTaskExecutor") ThreadPoolTaskExecutor taskExecutor
    ) {
        this.photoRepository = photoRepository;
        this.storageService = storageService;
        this.imageOptimizer = imageOptimizer;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
    }

    public void enqueue(Long photoId) {
        if (!optimizationEnabled() || photoId == null) {
            return;
        }
        try {
            taskExecutor.execute(() -> optimizeNow(photoId));
        } catch (TaskRejectedException ex) {
            log.warn("Image optimization queue rejected photo {}; scheduled processing will retry it.", photoId, ex);
        }
    }

    @Scheduled(fixedDelayString = "${nightout.image-optimization.poll-delay-ms:60000}", initialDelayString = "${nightout.image-optimization.initial-delay-ms:10000}")
    public void optimizePendingBatch() {
        if (!optimizationEnabled()) {
            return;
        }
        int batchSize = Math.max(1, properties.getImageOptimization().getBatchSize());
        photoRepository.findByOptimizationStatusOrderByUploadedAtAsc(PhotoOptimizationStatus.PENDING, PageRequest.of(0, batchSize))
                .forEach(photo -> enqueue(photo.getId()));
    }

    public void optimizeNow(Long photoId) {
        if (!optimizationEnabled()) {
            return;
        }

        Optional<PhotoWork> work = markStarted(photoId);
        if (work.isEmpty()) {
            return;
        }

        String optimizedStorageFileId = null;
        try {
            byte[] originalContent = readAll(work.get().storageFileId());
            OptimizedImage optimized = imageOptimizer.optimize(
                    originalContent,
                    properties.getImageOptimization().getMaxDimension(),
                    properties.getImageOptimization().getJpegQuality()
            );
            StorageFile stored = storageService.upload(
                    optimized.content(),
                    optimizedFilename(photoId, optimized.extension()),
                    optimized.mimeType(),
                    storagePrefix(work.get().storageFileId())
            );
            optimizedStorageFileId = stored.id();
            boolean completed = markComplete(photoId, work.get().storageFileId(), stored);
            if (!completed) {
                deleteQuietly(optimizedStorageFileId, "unused optimized image");
                return;
            }
            if (properties.getImageOptimization().isDeleteOriginal() && !stored.id().equals(work.get().storageFileId())) {
                deleteQuietly(work.get().storageFileId(), "original image");
            }
        } catch (RuntimeException ex) {
            if (StringUtils.hasText(optimizedStorageFileId)) {
                deleteQuietly(optimizedStorageFileId, "failed optimized image");
            }
            markFailed(photoId, ex);
        }
    }

    private Optional<PhotoWork> markStarted(Long photoId) {
        return transactionTemplate.execute(status -> {
            int updated = photoRepository.markOptimizationStarted(
                    photoId,
                    PhotoOptimizationStatus.PENDING,
                    PhotoOptimizationStatus.PROCESSING,
                    Instant.now(clock)
            );
            if (updated == 0) {
                return Optional.empty();
            }
            return photoRepository.findById(photoId)
                    .map(photo -> new PhotoWork(photo.getId(), photo.getStorageFileId()));
        });
    }

    private boolean markComplete(Long photoId, String originalStorageFileId, StorageFile stored) {
        Boolean completed = transactionTemplate.execute(status -> photoRepository.findById(photoId)
                .filter(photo -> originalStorageFileId.equals(photo.getStorageFileId()))
                .map(photo -> {
                    photo.setStorageFileId(stored.id());
                    photo.setSafeFilename(safeOptimizedFilename(photo, stored.filename()));
                    photo.setMimeType(stored.mimeType());
                    photo.setFileSize(stored.sizeBytes());
                    photo.setOptimizationStatus(PhotoOptimizationStatus.COMPLETE);
                    photo.setOptimizationError(null);
                    photo.setOptimizedAt(Instant.now(clock));
                    return true;
                })
                .orElse(false));
        return Boolean.TRUE.equals(completed);
    }

    private void markFailed(Long photoId, RuntimeException ex) {
        transactionTemplate.executeWithoutResult(status -> photoRepository.findById(photoId).ifPresent(photo -> {
            photo.setOptimizationStatus(PhotoOptimizationStatus.FAILED);
            photo.setOptimizationError(errorMessage(ex));
        }));
    }

    private byte[] readAll(String storageFileId) {
        StorageResource resource = storageService.retrieve(storageFileId);
        try {
            return resource.resource().getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new StorageException("Could not read stored image for optimization.", ex);
        }
    }

    private void deleteQuietly(String storageFileId, String label) {
        try {
            storageService.delete(storageFileId);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete {} {} after image optimization.", label, storageFileId, ex);
        }
    }

    private boolean optimizationEnabled() {
        return properties.getImageOptimization().isEnabled();
    }

    private static String storagePrefix(String storageFileId) {
        int slashIndex = storageFileId.lastIndexOf('/');
        return slashIndex < 0 ? "" : storageFileId.substring(0, slashIndex);
    }

    private static String optimizedFilename(Long photoId, String extension) {
        return "optimized-" + photoId + "-" + UUID.randomUUID() + "." + extension;
    }

    private static String safeOptimizedFilename(Photo photo, String storedFilename) {
        String baseName = photo.getSafeFilename();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        return baseName + "-" + storedFilename;
    }

    private static String errorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private record PhotoWork(Long photoId, String storageFileId) {
    }
}
