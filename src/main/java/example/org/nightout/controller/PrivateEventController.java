package example.org.nightout.controller;

import example.org.nightout.dto.PrivateEventView;
import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.PrivateEventPhotoService;
import example.org.nightout.service.PrivateEventService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@RequestMapping("/private-events")
public class PrivateEventController {

    private final PrivateEventService privateEventService;
    private final PrivateEventPhotoService privateEventPhotoService;
    private final AppProperties properties;

    public PrivateEventController(PrivateEventService privateEventService, PrivateEventPhotoService privateEventPhotoService, AppProperties properties) {
        this.privateEventService = privateEventService;
        this.privateEventPhotoService = privateEventPhotoService;
        this.properties = properties;
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("events", privateEventService.eventsFor(user));
        return "private-events/dashboard";
    }

    @GetMapping("/create")
    public String createForm() {
        return "private-events/create";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam String eventName,
                         @RequestParam(required = false) String location,
                         @RequestParam String password,
                         RedirectAttributes redirectAttributes) {
        try {
            PrivateEvent event = privateEventService.create(user, eventName, location, password);
            redirectAttributes.addFlashAttribute("successMessage", "Private event created.");
            redirectAttributes.addFlashAttribute("successInviteLink", inviteLink(event));
            redirectAttributes.addFlashAttribute("successInviteCode", event.getJoinCode());
            redirectAttributes.addFlashAttribute("successInvitePassword", password);
            return "redirect:/private-events/" + event.getJoinCode();
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/private-events/create";
        }
    }

    @GetMapping("/invite/{inviteToken}")
    public String invite(@AuthenticationPrincipal AuthenticatedUser user,
                         @PathVariable String inviteToken,
                         RedirectAttributes redirectAttributes) {
        try {
            PrivateEvent event = privateEventService.joinByInviteToken(user, inviteToken);
            redirectAttributes.addFlashAttribute("successMessage", "Private event added to your account.");
            return "redirect:/private-events/" + event.getJoinCode();
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/private-events";
        }
    }

    @GetMapping("/join")
    public String joinForm(@RequestParam(required = false) String code, Model model) {
        model.addAttribute("joinCode", code);
        return "private-events/join";
    }

    @PostMapping("/join")
    public String join(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam String joinCode,
                       @RequestParam String password,
                       RedirectAttributes redirectAttributes) {
        try {
            PrivateEvent event = privateEventService.join(user, joinCode, password);
            redirectAttributes.addFlashAttribute("successMessage", "You joined the private event.");
            return "redirect:/private-events/" + event.getJoinCode();
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("joinCode", joinCode);
            return "redirect:/private-events/join";
        }
    }

    @GetMapping("/{joinCode}")
    public String event(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String joinCode, Model model) {
        PrivateEventView view = privateEventService.viewForJoinCode(user, joinCode);
        model.addAttribute("eventView", view);
        if (!view.member()) {
            model.addAttribute("joinCode", joinCode);
            return "private-events/join";
        }
        model.addAttribute("photos", privateEventPhotoService.photosFor(user, joinCode));
        addUploadLimits(model);
        return "private-events/event";
    }

    @PostMapping(value = "/{joinCode}/upload", headers = "X-NightOut-Batch-Upload=true")
    @ResponseBody
    public ResponseEntity<UploadBatchResponse> uploadBatch(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @PathVariable String joinCode,
                                                           @RequestParam(value = "photos", required = false) MultipartFile[] photos) {
        String redirectUrl = eventUrl(joinCode);
        try {
            int count = privateEventPhotoService.uploadPhotos(user, joinCode, photos).size();
            return ResponseEntity.ok(UploadBatchResponse.success(count, redirectUrl));
        } catch (BusinessRuleException ex) {
            return ResponseEntity.badRequest().body(UploadBatchResponse.failure(ex.getMessage(), redirectUrl));
        }
    }

    @PostMapping("/{joinCode}/upload")
    public String upload(@AuthenticationPrincipal AuthenticatedUser user,
                         @PathVariable String joinCode,
                         @RequestParam(value = "photos", required = false) MultipartFile[] photos,
                         RedirectAttributes redirectAttributes) {
        try {
            int count = privateEventPhotoService.uploadPhotos(user, joinCode, photos).size();
            redirectAttributes.addFlashAttribute("successMessage", count + " photo" + (count == 1 ? "" : "s") + " uploaded successfully.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + eventUrl(joinCode);
    }

    private void addUploadLimits(Model model) {
        model.addAttribute("maxUploadCount", properties.getMaxUploadCount());
        model.addAttribute("maxUploadBytes", properties.getMaxUploadBytes());
        model.addAttribute("maxUploadBatchBytes", Math.max(1, properties.getMaxRequestBytes() * 9 / 10));
    }

    private static String eventUrl(String joinCode) {
        return "/private-events/" + joinCode;
    }

    private static String inviteLink(PrivateEvent event) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/private-events/invite/{token}")
                .buildAndExpand(event.getInviteToken())
                .toUriString();
    }

    private record UploadBatchResponse(boolean success, int count, String message, String redirectUrl) {
        static UploadBatchResponse success(int count, String redirectUrl) {
            return new UploadBatchResponse(true, count, null, redirectUrl);
        }

        static UploadBatchResponse failure(String message, String redirectUrl) {
            return new UploadBatchResponse(false, 0, message, redirectUrl);
        }
    }
}
