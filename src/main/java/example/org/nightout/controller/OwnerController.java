package example.org.nightout.controller;

import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.AdminQueryService;
import example.org.nightout.service.EventService;
import example.org.nightout.service.PhotoService;
import example.org.nightout.service.QrCodeService;
import example.org.nightout.service.UserManagementService;
import example.org.nightout.storage.StorageResource;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
public class OwnerController {

    private final UserManagementService userManagementService;
    private final EventService eventService;
    private final PhotoService photoService;
    private final QrCodeService qrCodeService;
    private final AdminQueryService adminQueryService;

    public OwnerController(UserManagementService userManagementService, EventService eventService, PhotoService photoService, QrCodeService qrCodeService, AdminQueryService adminQueryService) {
        this.userManagementService = userManagementService;
        this.eventService = eventService;
        this.photoService = photoService;
        this.qrCodeService = qrCodeService;
        this.adminQueryService = adminQueryService;
    }

    @GetMapping("/owner")
    public String dashboard(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("clubs", userManagementService.manageableClubs(user));
        return "owner/dashboard";
    }

    @GetMapping("/owner/clubs/{clubId}")
    public String club(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long clubId, Model model) {
        userManagementService.requireCanManageClub(user, clubId);
        Club club = userManagementService.manageableClubs(user).stream()
                .filter(candidate -> candidate.getId().equals(clubId))
                .findFirst()
                .orElseThrow();
        model.addAttribute("club", club);
        model.addAttribute("events", eventService.eventViewsForClub(club));
        model.addAttribute("photos", adminQueryService.photosForClub(clubId));
        return "owner/club";
    }

    @PostMapping("/owner/clubs/{clubId}/events")
    public String createEvent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long clubId, @RequestParam String eventName,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
                              RedirectAttributes redirectAttributes) {
        userManagementService.requireCanManageClub(user, clubId);
        eventService.create(clubId, eventName, eventDate, startTime, endTime);
        redirectAttributes.addFlashAttribute("successMessage", "Night created.");
        return "redirect:/owner/clubs/" + clubId;
    }

    @PostMapping("/owner/events/{eventId}/cancel")
    public String cancelEvent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long eventId, RedirectAttributes redirectAttributes) {
        NightEvent event = eventService.requireById(eventId);
        userManagementService.requireCanManageClub(user, event.getClub().getId());
        eventService.cancel(eventId);
        redirectAttributes.addFlashAttribute("successMessage", "Night cancelled.");
        return "redirect:/owner/clubs/" + event.getClub().getId();
    }

    @PostMapping("/owner/photos/{photoId}/remove")
    public String removePhoto(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long photoId, RedirectAttributes redirectAttributes) {
        Long clubId = photoService.clubIdForPhoto(photoId);
        userManagementService.requireCanManageClub(user, clubId);
        photoService.removePhoto(photoId);
        redirectAttributes.addFlashAttribute("successMessage", "Photo removed.");
        return "redirect:/owner/clubs/" + clubId;
    }

    @GetMapping("/owner/photos/{photoId}/download")
    public ResponseEntity<Resource> downloadPhoto(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long photoId) {
        Photo photo = photoService.requirePhoto(photoId);
        userManagementService.requireCanManageClub(user, photo.getEvent().getClub().getId());
        return downloadablePhoto(photo);
    }

    @GetMapping("/owner/clubs/{clubId}/qr.png")
    public ResponseEntity<byte[]> clubQr(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long clubId) {
        userManagementService.requireCanManageClub(user, clubId);
        Club club = userManagementService.manageableClubs(user).stream()
                .filter(candidate -> candidate.getId().equals(clubId))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrCodeService.clubQrPng(club));
    }

    private ResponseEntity<Resource> downloadablePhoto(Photo photo) {
        StorageResource stored = photoService.retrieve(photo);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(photo.getSafeFilename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getMimeType()))
                .contentLength(stored.contentLength())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(stored.resource());
    }
}
