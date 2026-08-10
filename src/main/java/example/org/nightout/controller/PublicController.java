package example.org.nightout.controller;

import example.org.nightout.dto.EventView;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.service.ClubService;
import example.org.nightout.service.EventService;
import example.org.nightout.service.NightlifeDateService;
import example.org.nightout.service.PhotoService;

import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.time.LocalDate;

@Controller
public class PublicController {

    private final ClubService clubService;
    private final EventService eventService;
    private final NightlifeDateService nightlifeDateService;
    private final PhotoService photoService;

    public PublicController(ClubService clubService, EventService eventService, NightlifeDateService nightlifeDateService, PhotoService photoService) {
        this.clubService = clubService;
        this.eventService = eventService;
        this.nightlifeDateService = nightlifeDateService;
        this.photoService = photoService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("clubs", clubService.activeClubs());
        return "home";
    }

    @GetMapping("/clubs/{slug}")
    public String club(@PathVariable String slug, Model model) {
        Club club = clubService.requireActiveBySlug(slug);
        var nightDates = eventService.nightDateViewsForClub(club, nightlifeDateService.currentAndPreviousNightDates(7));
        model.addAttribute("club", club);
        model.addAttribute("nightDates", nightDates.reversed());
        return "club";
    }

    @GetMapping("/clubs/{slug}/dates/{nightDate}")
    public String dateGallery(@PathVariable String slug,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nightDate,
                              Model model) {
        Club club = clubService.requireActiveBySlug(slug);
        List<EventView> events = eventService.eventViewsForClubAndDate(club, nightDate);
        model.addAttribute("club", club);
        model.addAttribute("nightDate", nightDate);
        model.addAttribute("events", events);
        model.addAttribute("uploadEvents", events.stream().filter(EventView::uploadAvailable).toList());
        model.addAttribute("dateUploadAvailable", eventService.uploadAvailableForDate(nightDate));
        model.addAttribute("photos", photoService.galleryPhotosForEvents(events.stream().map(EventView::event).toList()));
        return "date-gallery";
    }

    @GetMapping("/clubs/{slug}/events/{eventId}")
    public String event(@PathVariable String slug, @PathVariable Long eventId, Model model) {
        NightEvent event = eventService.requirePublicEvent(slug, eventId);
        model.addAttribute("club", event.getClub());
        model.addAttribute("eventView", eventService.viewFor(event));
        return "event";
    }

    @GetMapping("/clubs/{slug}/events/{eventId}/gallery")
    public String gallery(@PathVariable String slug, @PathVariable Long eventId, Model model) {
        NightEvent event = eventService.requirePublicEvent(slug, eventId);
        model.addAttribute("club", event.getClub());
        model.addAttribute("eventView", eventService.viewFor(event));
        model.addAttribute("photos", photoService.galleryPhotos(slug, eventId));
        return "gallery";
    }

    @GetMapping("/clubs/{slug}/events/{eventId}/upload")
    public String upload(@PathVariable String slug, @PathVariable Long eventId, Model model) {
        NightEvent event = eventService.requirePublicEvent(slug, eventId);
        EventView view = eventService.viewFor(event);
        model.addAttribute("club", event.getClub());
        model.addAttribute("eventView", view);
        if (!view.uploadAvailable()) {
            model.addAttribute("errorMessage", view.status().name().equals("UPCOMING") ? "This night has not happened yet." : "This gallery has expired.");
        }
        return "upload";
    }

    @PostMapping("/clubs/{slug}/events/{eventId}/upload")
    public String handleUpload(
            @PathVariable String slug,
            @PathVariable Long eventId,
            @RequestParam("photos") MultipartFile[] photos,
            @RequestParam(value = "returnDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int count = photoService.uploadPhotos(slug, eventId, photos).size();
            redirectAttributes.addFlashAttribute("successMessage", count + " photo" + (count == 1 ? "" : "s") + " uploaded successfully.");
            return uploadRedirect(slug, eventId, returnDate, false);
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return uploadRedirect(slug, eventId, returnDate, true);
        }
    }

    @PostMapping("/clubs/{slug}/dates/{nightDate}/upload")
    public String handleDateUpload(
            @PathVariable String slug,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nightDate,
            @RequestParam(value = "eventId", required = false) Long eventId,
            @RequestParam("photos") MultipartFile[] photos,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int count = photoService.uploadPhotosForDate(slug, nightDate, eventId, photos).size();
            redirectAttributes.addFlashAttribute("successMessage", count + " photo" + (count == 1 ? "" : "s") + " uploaded successfully.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/clubs/" + slug + "/dates/" + nightDate;
    }

    private static String uploadRedirect(String slug, Long eventId, LocalDate returnDate, boolean failed) {
        if (returnDate != null) {
            return "redirect:/clubs/" + slug + "/dates/" + returnDate;
        }
        return "redirect:/clubs/" + slug + "/events/" + eventId + (failed ? "/upload" : "/gallery");
    }
}
