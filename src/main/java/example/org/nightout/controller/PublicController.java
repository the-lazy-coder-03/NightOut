package example.org.nightout.controller;

import example.org.nightout.dto.EventView;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.service.ClubService;
import example.org.nightout.service.EventService;
import example.org.nightout.service.PhotoService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PublicController {

    private final ClubService clubService;
    private final EventService eventService;
    private final PhotoService photoService;

    public PublicController(ClubService clubService, EventService eventService, PhotoService photoService) {
        this.clubService = clubService;
        this.eventService = eventService;
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
        List<EventView> events = eventService.eventViewsForClub(club);
        Map<Boolean, List<EventView>> grouped = events.stream()
                .collect(Collectors.partitioningBy(EventView::uploadAvailable));
        model.addAttribute("club", club);
        model.addAttribute("recentEvents", grouped.get(true));
        model.addAttribute("otherEvents", grouped.get(false));
        return "club";
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
            RedirectAttributes redirectAttributes
    ) {
        try {
            int count = photoService.uploadPhotos(slug, eventId, photos).size();
            redirectAttributes.addFlashAttribute("successMessage", count + " photo" + (count == 1 ? "" : "s") + " uploaded successfully.");
            return "redirect:/clubs/" + slug + "/events/" + eventId + "/gallery";
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/clubs/" + slug + "/events/" + eventId + "/upload";
        }
    }
}
