package example.org.nightout.controller;

import example.org.nightout.security.AuthenticatedUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user != null && user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/admin";
        }
        if (user != null && user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_CLUB_OWNER"))) {
            return "redirect:/owner";
        }
        if (user != null) {
            return "redirect:/private-events";
        }
        return "redirect:/owner";
    }

    @GetMapping("/login")
    public String login() {
        return "redirect:/oauth2/authorization/logto";
    }
}
