package example.org.nightout.controller;

import example.org.nightout.entity.PrivateEventPhoto;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.PrivateEventPhotoService;
import example.org.nightout.storage.StorageResource;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PrivateEventPhotoController {

    private final PrivateEventPhotoService privateEventPhotoService;

    public PrivateEventPhotoController(PrivateEventPhotoService privateEventPhotoService) {
        this.privateEventPhotoService = privateEventPhotoService;
    }

    @GetMapping("/private-event-photos/{photoId}")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long photoId) {
        PrivateEventPhoto photo = privateEventPhotoService.privatePhoto(user, photoId);
        StorageResource stored = privateEventPhotoService.retrieve(photo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getMimeType()))
                .contentLength(stored.contentLength())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag("\"private-event-photo-" + photo.getId() + "-" + photo.getFileSize() + "\"")
                .lastModified(photo.getUploadedAt().toEpochMilli())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + photo.getSafeFilename() + "\"")
                .body(stored.resource());
    }
}
