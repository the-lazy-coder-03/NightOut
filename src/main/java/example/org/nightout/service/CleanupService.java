package example.org.nightout.service;

import example.org.nightout.entity.Photo;
import example.org.nightout.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final PhotoService photoService;
    private final StorageService storageService;

    public CleanupService(PhotoService photoService, StorageService storageService) {
        this.photoService = photoService;
        this.storageService = storageService;
    }

    @Scheduled(cron = "0 17 * * * *")
    public void cleanupExpiredPhotos() {
        List<Photo> photos = photoService.expiredPhotos();
        for (Photo photo : photos) {
            try {
                storageService.delete(photo.getStorageFileId());
                photoService.deleteRecord(photo);
            } catch (RuntimeException ex) {
                log.warn("Failed to clean up photo {} from storage; it will be retried later.", photo.getId(), ex);
            }
        }
    }
}
