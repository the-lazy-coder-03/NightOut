package example.org.nightout.controller.api;

import example.org.nightout.config.AppProperties;
import example.org.nightout.dto.EventView;
import example.org.nightout.dto.NightDateView;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.model.Area;
import example.org.nightout.service.ClubService;
import example.org.nightout.service.EventService;
import example.org.nightout.service.NightlifeDateService;
import example.org.nightout.service.PhotoService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PublicApiController {

    private final ClubService clubService;
    private final EventService eventService;
    private final NightlifeDateService nightlifeDateService;
    private final PhotoService photoService;
    private final AppProperties properties;

    public PublicApiController(ClubService clubService, EventService eventService, NightlifeDateService nightlifeDateService, PhotoService photoService, AppProperties properties) {
        this.clubService = clubService;
        this.eventService = eventService;
        this.nightlifeDateService = nightlifeDateService;
        this.photoService = photoService;
        this.properties = properties;
    }

    @GetMapping("/areas")
    public AreasResponse areas() {
        return new AreasResponse(clubService.areas().stream()
                .map(PublicApiController::areaResponse)
                .toList());
    }

    @GetMapping("/areas/{areaSlug}/clubs")
    public AreaClubsResponse clubsForArea(@PathVariable String areaSlug) {
        Area area = clubService.requireAreaBySlug(areaSlug);
        return new AreaClubsResponse(
                areaResponse(area),
                clubService.activeClubsForArea(area).stream()
                        .map(this::clubSummary)
                        .toList()
        );
    }

    @GetMapping("/clubs/{clubSlug}")
    public ClubResponse club(@PathVariable String clubSlug) {
        Club club = clubService.requireActiveBySlug(clubSlug);
        List<NightDateResponse> nightDates = eventService
                .nightDateViewsForClub(club, nightlifeDateService.currentAndPreviousNightDates(7))
                .reversed()
                .stream()
                .map(PublicApiController::nightDateResponse)
                .toList();
        return new ClubResponse(clubSummary(club), nightDates);
    }

    @GetMapping("/clubs/{clubSlug}/dates/{nightDate}")
    public DateGalleryResponse dateGallery(
            @PathVariable String clubSlug,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nightDate
    ) {
        Club club = clubService.requireActiveBySlug(clubSlug);
        List<EventView> events = eventService.eventViewsForClubAndDate(club, nightDate);
        List<NightEvent> galleryEvents = events.stream().map(EventView::event).toList();
        return new DateGalleryResponse(
                clubSummary(club),
                nightDate,
                eventService.uploadAvailableForDate(nightDate),
                uploadLimits(),
                events.stream().map(PublicApiController::eventResponse).toList(),
                events.stream()
                        .filter(EventView::uploadAvailable)
                        .map(PublicApiController::eventResponse)
                        .toList(),
                photoService.galleryPhotosForEvents(galleryEvents).stream()
                        .map(PublicApiController::photoResponse)
                        .toList()
        );
    }

    @GetMapping("/clubs/{clubSlug}/events/{eventId}/gallery")
    public EventGalleryResponse eventGallery(@PathVariable String clubSlug, @PathVariable Long eventId) {
        NightEvent event = eventService.requirePublicEvent(clubSlug, eventId);
        return new EventGalleryResponse(
                clubSummary(event.getClub()),
                eventResponse(eventService.viewFor(event)),
                uploadLimits(),
                photoService.galleryPhotos(clubSlug, eventId).stream()
                        .map(PublicApiController::photoResponse)
                        .toList()
        );
    }

    @PostMapping("/clubs/{clubSlug}/dates/{nightDate}/upload")
    public ResponseEntity<UploadResponse> uploadForDate(
            @PathVariable String clubSlug,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nightDate,
            @RequestParam(value = "eventId", required = false) Long eventId,
            @RequestParam(value = "photos", required = false) MultipartFile[] photos
    ) {
        try {
            List<Photo> uploaded = photoService.uploadPhotosForDate(clubSlug, nightDate, eventId, photos);
            return ResponseEntity.ok(UploadResponse.success(uploaded));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(UploadResponse.failure(ex.getMessage()));
        } catch (BusinessRuleException ex) {
            return ResponseEntity.badRequest().body(UploadResponse.failure(ex.getMessage()));
        }
    }

    @PostMapping("/clubs/{clubSlug}/events/{eventId}/upload")
    public ResponseEntity<UploadResponse> uploadForEvent(
            @PathVariable String clubSlug,
            @PathVariable Long eventId,
            @RequestParam(value = "photos", required = false) MultipartFile[] photos
    ) {
        try {
            List<Photo> uploaded = photoService.uploadPhotos(clubSlug, eventId, photos);
            return ResponseEntity.ok(UploadResponse.success(uploaded));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(UploadResponse.failure(ex.getMessage()));
        } catch (BusinessRuleException ex) {
            return ResponseEntity.badRequest().body(UploadResponse.failure(ex.getMessage()));
        }
    }

    private static AreaResponse areaResponse(Area area) {
        return new AreaResponse(area.getDisplayName(), area.getSlug());
    }

    private ClubSummaryResponse clubSummary(Club club) {
        return new ClubSummaryResponse(
                club.getId(),
                club.getName(),
                club.getSlug(),
                club.getCity(),
                club.getArea(),
                club.getAddress(),
                clubImageUrl(club)
        );
    }

    private static NightDateResponse nightDateResponse(NightDateView view) {
        return new NightDateResponse(
                view.date(),
                view.current(),
                view.photoCount(),
                view.events().stream().map(PublicApiController::eventResponse).toList()
        );
    }

    private static EventResponse eventResponse(EventView view) {
        NightEvent event = view.event();
        return new EventResponse(
                event.getId(),
                event.getEventName(),
                event.getEventDate(),
                event.getStartTime(),
                event.getEndTime(),
                view.expiresOn(),
                view.status().name(),
                view.photoCount(),
                view.uploadAvailable(),
                view.galleryAvailable()
        );
    }

    private static PhotoResponse photoResponse(Photo photo) {
        return new PhotoResponse(
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

    private UploadLimitsResponse uploadLimits() {
        return new UploadLimitsResponse(
                properties.getMaxUploadCount(),
                properties.getMaxUploadBytes(),
                Math.max(1, properties.getMaxRequestBytes() * 9 / 10)
        );
    }

    private static String clubImageUrl(Club club) {
        if (club.hasUploadedImage()) {
            return "/club-images/" + club.getId();
        }
        return club.getLogoUrl();
    }

    private static String photoUrl(Photo photo) {
        return "/photos/" + photo.getId();
    }

    private static String photoDownloadUrl(Photo photo) {
        return photoUrl(photo) + "/download";
    }

    public record AreasResponse(List<AreaResponse> areas) {
    }

    public record AreaResponse(String displayName, String slug) {
    }

    public record AreaClubsResponse(AreaResponse area, List<ClubSummaryResponse> clubs) {
    }

    public record ClubSummaryResponse(Long id, String name, String slug, String city, String area, String address, String imageUrl) {
    }

    public record ClubResponse(ClubSummaryResponse club, List<NightDateResponse> nightDates) {
    }

    public record NightDateResponse(LocalDate date, boolean current, long photoCount, List<EventResponse> events) {
    }

    public record DateGalleryResponse(
            ClubSummaryResponse club,
            LocalDate nightDate,
            boolean uploadAvailable,
            UploadLimitsResponse uploadLimits,
            List<EventResponse> events,
            List<EventResponse> uploadEvents,
            List<PhotoResponse> photos
    ) {
    }

    public record EventGalleryResponse(
            ClubSummaryResponse club,
            EventResponse event,
            UploadLimitsResponse uploadLimits,
            List<PhotoResponse> photos
    ) {
    }

    public record EventResponse(
            Long id,
            String name,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate expiresOn,
            String status,
            long photoCount,
            boolean uploadAvailable,
            boolean galleryAvailable
    ) {
    }

    public record PhotoResponse(
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

    public record UploadLimitsResponse(int maxUploadCount, long maxUploadBytes, long maxUploadBatchBytes) {
    }

    public record UploadResponse(boolean success, int count, String message, List<PhotoResponse> photos) {
        static UploadResponse success(List<Photo> photos) {
            return new UploadResponse(
                    true,
                    photos.size(),
                    null,
                    photos.stream().map(PublicApiController::photoResponse).toList()
            );
        }

        static UploadResponse failure(String message) {
            return new UploadResponse(false, 0, message, List.of());
        }
    }
}
