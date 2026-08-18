package example.org.nightout.controller.api;

import example.org.nightout.config.AppProperties;
import example.org.nightout.dto.PrivateEventView;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventPhoto;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.PrivateEventPhotoService;
import example.org.nightout.service.PrivateEventService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/private-events")
public class PrivateEventApiController {

    private final PrivateEventService privateEventService;
    private final PrivateEventPhotoService privateEventPhotoService;
    private final AppProperties properties;

    public PrivateEventApiController(PrivateEventService privateEventService,
                                     PrivateEventPhotoService privateEventPhotoService,
                                     AppProperties properties) {
        this.privateEventService = privateEventService;
        this.privateEventPhotoService = privateEventPhotoService;
        this.properties = properties;
    }

    @GetMapping
    public PrivateEventsResponse events(@AuthenticationPrincipal AuthenticatedUser user) {
        return new PrivateEventsResponse(privateEventService.eventsFor(user).stream()
                .map(view -> eventResponse(view, photoCount(user, view), false))
                .toList());
    }

    @PostMapping
    public PrivateEventDetailResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestBody CreatePrivateEventRequest request) {
        PrivateEvent event = privateEventService.create(
                user,
                request.eventName(),
                request.password(),
                request.guestSharingAllowed()
        );
        return detailResponse(user, event.getJoinCode());
    }

    @PostMapping("/join")
    public PrivateEventDetailResponse join(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestBody JoinPrivateEventRequest request) {
        PrivateEvent event = privateEventService.join(user, request.joinCode(), request.password());
        return detailResponse(user, event.getJoinCode());
    }

    @PostMapping("/invite/{inviteToken}")
    public PrivateEventDetailResponse joinInvite(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable String inviteToken) {
        PrivateEvent event = privateEventService.joinByInviteToken(user, inviteToken);
        return detailResponse(user, event.getJoinCode());
    }

    @GetMapping("/{joinCode}")
    public PrivateEventDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable String joinCode) {
        return detailResponse(user, joinCode);
    }

    @PostMapping("/{joinCode}/upload")
    public ResponseEntity<PrivateUploadResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable String joinCode,
                                                       @RequestParam(value = "photos", required = false) MultipartFile[] photos) {
        try {
            List<PrivateEventPhoto> uploaded = privateEventPhotoService.uploadPhotos(user, joinCode, photos);
            return ResponseEntity.ok(PrivateUploadResponse.success(uploaded));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(PrivateUploadResponse.failure(ex.getMessage()));
        } catch (BusinessRuleException ex) {
            return ResponseEntity.badRequest().body(PrivateUploadResponse.failure(ex.getMessage()));
        }
    }

    private PrivateEventDetailResponse detailResponse(AuthenticatedUser user, String joinCode) {
        PrivateEventView view = privateEventService.viewForJoinCode(user, joinCode);
        List<PrivateEventPhotoResponse> photos = view.member()
                ? privateEventPhotoService.photosFor(user, joinCode).stream()
                .map(PrivateEventApiController::photoResponse)
                .toList()
                : List.of();
        return new PrivateEventDetailResponse(
                eventResponse(view, photos.size(), true),
                uploadLimits(),
                photos
        );
    }

    private int photoCount(AuthenticatedUser user, PrivateEventView view) {
        if (!view.member()) {
            return 0;
        }
        return privateEventPhotoService.photosFor(user, view.event().getJoinCode()).size();
    }

    private PrivateEventResponse eventResponse(PrivateEventView view, int photoCount, boolean includeUploadLimits) {
        PrivateEvent event = view.event();
        boolean canShare = view.creator() || event.isGuestSharingAllowed();
        return new PrivateEventResponse(
                event.getId(),
                event.getEventName(),
                event.getJoinCode(),
                event.getEventDate(),
                event.getStartTime(),
                event.getEndTime(),
                view.expiresOn(),
                view.member(),
                view.creator(),
                event.isGuestSharingAllowed(),
                canShare,
                canShare && event.getInviteToken() != null ? inviteLink(event) : null,
                canShare ? event.getSharePassword() : null,
                photoCount,
                includeUploadLimits ? uploadLimits() : null
        );
    }

    private UploadLimitsResponse uploadLimits() {
        return new UploadLimitsResponse(
                properties.getMaxUploadCount(),
                properties.getMaxUploadBytes(),
                Math.max(1, properties.getMaxRequestBytes() * 9 / 10)
        );
    }

    private String inviteLink(PrivateEvent event) {
        return UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/private-events/invite/{token}")
                .buildAndExpand(event.getInviteToken())
                .toUriString();
    }

    private static PrivateEventPhotoResponse photoResponse(PrivateEventPhoto photo) {
        return new PrivateEventPhotoResponse(
                photo.getId(),
                photo.getOriginalFilename(),
                photo.getSafeFilename(),
                photo.getMimeType(),
                photo.getFileSize(),
                photo.getUploadedAt(),
                photoUrl(photo),
                photoDownloadUrl(photo)
        );
    }

    private static String photoUrl(PrivateEventPhoto photo) {
        return "/private-event-photos/" + photo.getId();
    }

    private static String photoDownloadUrl(PrivateEventPhoto photo) {
        return photoUrl(photo) + "/download";
    }

    public record PrivateEventsResponse(List<PrivateEventResponse> events) {
    }

    public record PrivateEventDetailResponse(
            PrivateEventResponse event,
            UploadLimitsResponse uploadLimits,
            List<PrivateEventPhotoResponse> photos
    ) {
    }

    public record CreatePrivateEventRequest(String eventName, String password, boolean guestSharingAllowed) {
    }

    public record JoinPrivateEventRequest(String joinCode, String password) {
    }

    public record PrivateEventResponse(
            Long id,
            String name,
            String joinCode,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate expiresOn,
            boolean member,
            boolean creator,
            boolean guestSharingAllowed,
            boolean canShare,
            String inviteUrl,
            String sharePassword,
            int photoCount,
            UploadLimitsResponse uploadLimits
    ) {
    }

    public record UploadLimitsResponse(int maxUploadCount, long maxUploadBytes, long maxUploadBatchBytes) {
    }

    public record PrivateEventPhotoResponse(
            Long id,
            String originalFilename,
            String safeFilename,
            String mimeType,
            long fileSize,
            Instant uploadedAt,
            String url,
            String downloadUrl
    ) {
    }

    public record PrivateUploadResponse(boolean success, int count, String message, List<PrivateEventPhotoResponse> photos) {
        static PrivateUploadResponse success(List<PrivateEventPhoto> photos) {
            return new PrivateUploadResponse(
                    true,
                    photos.size(),
                    null,
                    photos.stream().map(PrivateEventApiController::photoResponse).toList()
            );
        }

        static PrivateUploadResponse failure(String message) {
            return new PrivateUploadResponse(false, 0, message, List.of());
        }
    }
}
