package example.org.nightout.controller;

import example.org.nightout.dto.PrivateEventView;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.PrivateEventService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
@RequestMapping("/private-events")
public class PrivateEventController {

    private final PrivateEventService privateEventService;

    public PrivateEventController(PrivateEventService privateEventService) {
        this.privateEventService = privateEventService;
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("events", privateEventService.eventsFor(user));
        return "private-events/dashboard";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam String eventName,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
                         @RequestParam(required = false) String location,
                         @RequestParam String password,
                         RedirectAttributes redirectAttributes) {
        try {
            PrivateEvent event = privateEventService.create(user, eventName, eventDate, startTime, endTime, location, password);
            redirectAttributes.addFlashAttribute("successMessage", "Private event created. Share code " + event.getJoinCode() + " and the password with your guests.");
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
        return "private-events/event";
    }
}
