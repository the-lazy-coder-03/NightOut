package example.org.nightout.security;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("ownerAuthorization")
public class OwnerAuthorizationService {

    private final AppUserRepository userRepository;

    public OwnerAuthorizationService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean canManageClub(Authentication authentication, Long clubId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return false;
        }
        if (principal.hasRole(UserRole.ADMIN)) {
            return true;
        }
        if (!principal.hasRole(UserRole.CLUB_OWNER)) {
            return false;
        }
        AppUser user = userRepository.findById(principal.getId()).orElse(null);
        return user != null && user.getClubs().stream().anyMatch(club -> club.getId().equals(clubId));
    }
}
