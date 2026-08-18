package example.org.nightout.controller;

import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoOptimizationStatus;
import example.org.nightout.service.PhotoService;
import example.org.nightout.storage.StorageResource;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.util.List;

@Controller
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/photos/download")
    public ResponseEntity<StreamingResponseBody> downloadSelected(@RequestParam(name = "photoIds", required = false) List<Long> photoIds) {
        List<PhotoArchive.Entry> entries = PhotoArchive.selectedIds(photoIds).stream()
                .map(photoService::publicPhoto)
                .map(photo -> new PhotoArchive.Entry(photo.getSafeFilename(), photoService.retrieve(photo).resource()))
                .toList();
        return PhotoArchive.zip("crowdcam-photos.zip", entries);
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<Resource> photo(@PathVariable Long photoId) {
        return photoResponse(photoId, false);
    }

    @GetMapping("/photos/{photoId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long photoId) {
        return photoResponse(photoId, true);
    }

    private ResponseEntity<Resource> photoResponse(Long photoId, boolean download) {
        Photo photo = photoService.publicPhoto(photoId);
        StorageResource stored = photoService.retrieve(photo);
        CacheControl cacheControl = photo.getOptimizationStatus() == PhotoOptimizationStatus.COMPLETE
                ? CacheControl.maxAge(Duration.ofDays(7)).cachePublic()
                : CacheControl.noCache().cachePublic();
        long lastModified = photo.getOptimizedAt() == null ? photo.getUploadedAt().toEpochMilli() : photo.getOptimizedAt().toEpochMilli();
        ContentDisposition contentDisposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(photo.getSafeFilename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getMimeType()))
                .contentLength(stored.contentLength())
                .cacheControl(cacheControl)
                .eTag("\"photo-" + photo.getId() + "-" + photo.getFileSize() + "-" + photo.getOptimizationStatus() + "\"")
                .lastModified(lastModified)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(stored.resource());
    }
}
