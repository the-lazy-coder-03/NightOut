package example.org.nightout.controller;

import example.org.nightout.entity.Club;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.service.ClubImageService;
import example.org.nightout.service.ClubService;
import example.org.nightout.storage.StorageResource;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

@Controller
public class ClubImageController {

    private final ClubService clubService;
    private final ClubImageService clubImageService;

    public ClubImageController(ClubService clubService, ClubImageService clubImageService) {
        this.clubService = clubService;
        this.clubImageService = clubImageService;
    }

    @GetMapping("/club-images/{clubId}")
    public ResponseEntity<Resource> clubImage(@PathVariable Long clubId) {
        Club club = clubService.requireById(clubId);
        if (!club.hasUploadedImage()) {
            throw new ResourceNotFoundException("Club image not found.");
        }

        StorageResource stored = clubImageService.retrieve(club);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(club.getImageMimeType()))
                .contentLength(stored.contentLength())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .eTag("\"club-image-" + club.getId() + "-" + imageSize(club, stored) + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + club.getSlug() + "-club-image.jpg\"");
        if (club.getImageUploadedAt() != null) {
            response.lastModified(club.getImageUploadedAt().toEpochMilli());
        }
        return response.body(stored.resource());
    }

    private static long imageSize(Club club, StorageResource stored) {
        return club.getImageFileSize() == null ? stored.contentLength() : club.getImageFileSize();
    }
}
