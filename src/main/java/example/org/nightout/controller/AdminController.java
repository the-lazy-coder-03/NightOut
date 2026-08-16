package example.org.nightout.controller;

import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.service.AdminQueryService;
import example.org.nightout.service.ClubService;
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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Controller
public class AdminController {

    private final ClubService clubService;
    private final EventService eventService;
    private final PhotoService photoService;
    private final QrCodeService qrCodeService;
    private final UserManagementService userManagementService;
    private final AdminQueryService adminQueryService;

    public AdminController(ClubService clubService, EventService eventService, PhotoService photoService, QrCodeService qrCodeService, UserManagementService userManagementService, AdminQueryService adminQueryService) {
        this.clubService = clubService;
        this.eventService = eventService;
        this.photoService = photoService;
        this.qrCodeService = qrCodeService;
        this.userManagementService = userManagementService;
        this.adminQueryService = adminQueryService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("clubs", clubService.allClubs());
        model.addAttribute("events", adminQueryService.allEvents());
        model.addAttribute("photos", adminQueryService.allPhotos());
        model.addAttribute("owners", userManagementService.owners());
        model.addAttribute("areas", clubService.areas());
        return "admin/dashboard";
    }

    @PostMapping("/admin/clubs")
    public String createClub(@RequestParam String name, @RequestParam(required = false) String slug, @RequestParam String city,
                             @RequestParam String area,
                             @RequestParam(required = false) String address, @RequestParam(required = false) MultipartFile clubImage,
                             @RequestParam(required = false) String storageFolderId, RedirectAttributes redirectAttributes) {
        try {
            clubService.create(name, slug, city, area, address, clubImage, storageFolderId, true);
            redirectAttributes.addFlashAttribute("successMessage", "Club created.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/clubs/{clubId}")
    public String updateClub(@PathVariable Long clubId, @RequestParam String name, @RequestParam(required = false) String slug,
                             @RequestParam String city, @RequestParam String area, @RequestParam(required = false) String address,
                             @RequestParam(required = false) MultipartFile clubImage, @RequestParam(required = false) String storageFolderId,
                             @RequestParam(defaultValue = "false") boolean active, RedirectAttributes redirectAttributes) {
        try {
            clubService.update(clubId, name, slug, city, area, address, clubImage, storageFolderId, active);
            redirectAttributes.addFlashAttribute("successMessage", "Club updated.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/clubs/" + clubId + "/edit";
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/clubs/{clubId}/disable")
    public String disableClub(@PathVariable Long clubId, RedirectAttributes redirectAttributes) {
        clubService.disable(clubId);
        redirectAttributes.addFlashAttribute("successMessage", "Club disabled.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/events")
    public String createEvent(@RequestParam Long clubId, @RequestParam String eventName,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
                              RedirectAttributes redirectAttributes) {
        eventService.create(clubId, eventName, eventDate, startTime, endTime);
        redirectAttributes.addFlashAttribute("successMessage", "Night created.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/events/{eventId}")
    public String updateEvent(@PathVariable Long eventId, @RequestParam String eventName,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
                              @RequestParam(defaultValue = "false") boolean cancelled,
                              RedirectAttributes redirectAttributes) {
        eventService.update(eventId, eventName, eventDate, startTime, endTime, cancelled);
        redirectAttributes.addFlashAttribute("successMessage", "Night updated.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/events/{eventId}/delete")
    public String deleteEvent(@PathVariable Long eventId, RedirectAttributes redirectAttributes) {
        eventService.delete(eventId);
        redirectAttributes.addFlashAttribute("successMessage", "Night deleted.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/owners")
    public String linkOwner(@RequestParam String email, @RequestParam String fullName,
                            @RequestParam(required = false) List<Long> clubIds, RedirectAttributes redirectAttributes) {
        userManagementService.linkOwner(email, fullName, clubIds == null ? List.of() : clubIds);
        redirectAttributes.addFlashAttribute("successMessage", "Club owner linked. Create or invite the same email in Logto and assign the club_owner role.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/photos/{photoId}/remove")
    public String removePhoto(@PathVariable Long photoId, RedirectAttributes redirectAttributes) {
        photoService.removePhoto(photoId);
        redirectAttributes.addFlashAttribute("successMessage", "Photo removed from storage and database.");
        return "redirect:/admin";
    }

    @GetMapping("/admin/photos/{photoId}/download")
    public ResponseEntity<Resource> downloadPhoto(@PathVariable Long photoId) {
        return downloadablePhoto(photoService.requirePhoto(photoId));
    }

    @GetMapping("/admin/photos/download")
    public ResponseEntity<StreamingResponseBody> downloadSelectedPhotos(@RequestParam(name = "photoIds", required = false) List<Long> photoIds) {
        List<PhotoArchive.Entry> entries = PhotoArchive.selectedIds(photoIds).stream()
                .map(photoService::requirePhoto)
                .map(photo -> new PhotoArchive.Entry(photo.getSafeFilename(), photoService.retrieve(photo).resource()))
                .toList();
        return PhotoArchive.zip("nightout-photos.zip", entries);
    }

    @PostMapping("/admin/photos/delete-all")
    public String deleteAllPhotos(RedirectAttributes redirectAttributes) {
        PhotoService.PhotoDeleteResult result = photoService.deleteAllPhotos();
        if (result.hasFailures()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    result.deletedCount() + " photo" + plural(result.deletedCount()) + " deleted. "
                            + result.failedCount() + " photo" + plural(result.failedCount()) + " could not be deleted and remain listed."
            );
        } else {
            redirectAttributes.addFlashAttribute("successMessage", result.deletedCount() + " photo" + plural(result.deletedCount()) + " deleted.");
        }
        return "redirect:/admin";
    }

    @GetMapping("/admin/clubs/{clubId}/qr.png")
    public ResponseEntity<byte[]> clubQr(@PathVariable Long clubId) {
        Club club = clubService.requireById(clubId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrCodeService.clubQrPng(club));
    }

    @GetMapping("/admin/events/{eventId}/edit")
    public String editEvent(@PathVariable Long eventId, Model model) {
        NightEvent event = eventService.requireById(eventId);
        model.addAttribute("event", event);
        model.addAttribute("clubs", clubService.allClubs());
        return "admin/event-edit";
    }

    @GetMapping("/admin/clubs/{clubId}/edit")
    public String editClub(@PathVariable Long clubId, Model model) {
        model.addAttribute("club", clubService.requireById(clubId));
        model.addAttribute("areas", clubService.areas());
        return "admin/club-edit";
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
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
