package example.org.nightout.controller;

import example.org.nightout.entity.PrivateEventPhoto;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.PrivateEventPhotoService;
import example.org.nightout.storage.StorageResource;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@Controller
public class PrivateEventPhotoController {

    private final PrivateEventPhotoService privateEventPhotoService;

    public PrivateEventPhotoController(PrivateEventPhotoService privateEventPhotoService) {
        this.privateEventPhotoService = privateEventPhotoService;
    }

    @GetMapping("/private-event-photos/download")
    public ResponseEntity<StreamingResponseBody> downloadSelected(@AuthenticationPrincipal AuthenticatedUser user,
                                                                  @RequestParam(name = "photoIds", required = false) List<Long> photoIds) {
        List<PhotoArchive.Entry> entries = PhotoArchive.selectedIds(photoIds).stream()
                .map(photoId -> privateEventPhotoService.privatePhoto(user, photoId))
                .map(photo -> new PhotoArchive.Entry(photo.getSafeFilename(), privateEventPhotoService.retrieve(photo).resource()))
                .toList();
        return PhotoArchive.zip("private-event-photos.zip", entries);
    }

    @GetMapping("/private-event-photos/{photoId}")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long photoId) {
        return photoResponse(user, photoId, false);
    }

    @GetMapping("/private-event-photos/{photoId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long photoId) {
        return photoResponse(user, photoId, true);
    }

    private ResponseEntity<Resource> photoResponse(AuthenticatedUser user, Long photoId, boolean download) {
        PrivateEventPhoto photo = privateEventPhotoService.privatePhoto(user, photoId);
        StorageResource stored = privateEventPhotoService.retrieve(photo);
        ContentDisposition contentDisposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(photo.getSafeFilename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getMimeType()))
                .contentLength(stored.contentLength())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag("\"private-event-photo-" + photo.getId() + "-" + photo.getFileSize() + "\"")
                .lastModified(photo.getUploadedAt().toEpochMilli())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(stored.resource());
    }
}
