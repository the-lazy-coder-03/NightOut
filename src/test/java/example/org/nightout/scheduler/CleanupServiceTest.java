package example.org.nightout.scheduler;

import example.org.nightout.entity.Photo;
import example.org.nightout.exception.StorageException;
import example.org.nightout.service.CleanupService;
import example.org.nightout.service.PhotoService;
import example.org.nightout.storage.StorageService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleanupServiceTest {

    @Test
    void deletesStorageAndDatabaseRecordPerExpiredPhotoAndContinuesAfterFailure() {
        Photo successful = photo("storage-ok");
        Photo failed = photo("storage-fails");
        PhotoService photoService = mock(PhotoService.class);
        StorageService storageService = mock(StorageService.class);
        when(photoService.expiredPhotos()).thenReturn(List.of(successful, failed));
        doThrow(new StorageException("delete failed")).when(storageService).delete("storage-fails");

        CleanupService cleanupService = new CleanupService(photoService, storageService);
        cleanupService.cleanupExpiredPhotos();

        verify(storageService).delete("storage-ok");
        verify(storageService).delete("storage-fails");
        verify(photoService).deleteRecord(successful);
        verify(photoService, never()).deleteRecord(failed);
    }

    private Photo photo(String storageFileId) {
        Photo photo = new Photo();
        photo.setStorageFileId(storageFileId);
        return photo;
    }
}
