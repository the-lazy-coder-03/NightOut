package example.org.nightout.service;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.UserRole;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.security.AuthenticatedUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserManagementService {

    private final AppUserRepository userRepository;
    private final ClubRepository clubRepository;

    public UserManagementService(AppUserRepository userRepository, ClubRepository clubRepository) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Transactional(readOnly = true)
    public List<AppUser> owners() {
        return userRepository.findByRoleOrderByFullNameAsc(UserRole.CLUB_OWNER);
    }

    @Transactional(readOnly = true)
    public List<Club> manageableClubs(AuthenticatedUser principal) {
        AppUser user = requireUser(principal.getId());
        if (principal.hasRole(UserRole.ADMIN)) {
            return clubRepository.findAll();
        }
        if (!principal.hasRole(UserRole.CLUB_OWNER)) {
            return List.of();
        }
        return new ArrayList<>(user.getClubs());
    }

    @Transactional(readOnly = true)
    public List<Club> manageableClubs(Long userId) {
        AppUser user = requireUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            return clubRepository.findAll();
        }
        return new ArrayList<>(user.getClubs());
    }

    @Transactional(readOnly = true)
    public void requireCanManageClub(AuthenticatedUser principal, Long clubId) {
        AppUser user = requireUser(principal.getId());
        if (principal.hasRole(UserRole.ADMIN)) {
            return;
        }
        if (!principal.hasRole(UserRole.CLUB_OWNER)) {
            throw new BusinessRuleException("You cannot manage this club.");
        }
        boolean allowed = user.getClubs().stream().anyMatch(club -> club.getId().equals(clubId));
        if (!allowed) {
            throw new BusinessRuleException("You cannot manage this club.");
        }
    }

    @Transactional(readOnly = true)
    public void requireCanManageClub(Long userId, Long clubId) {
        AppUser user = requireUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        boolean allowed = user.getClubs().stream().anyMatch(club -> club.getId().equals(clubId));
        if (!allowed) {
            throw new BusinessRuleException("You cannot manage this club.");
        }
    }

    @Transactional
    public AppUser linkOwner(String email, String fullName, List<Long> clubIds) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(AppUser::new);
        user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        user.setFullName(fullName.trim());
        user.setPasswordHash(null);
        user.setRole(UserRole.CLUB_OWNER);
        user.getClubs().clear();
        for (Long clubId : clubIds) {
            clubRepository.findById(clubId).ifPresent(user.getClubs()::add);
        }
        return userRepository.save(user);
    }

    @Transactional
    public void assignOwnerToClub(Long ownerId, Long clubId) {
        AppUser owner = requireUser(ownerId);
        if (owner.getRole() != UserRole.CLUB_OWNER) {
            throw new BusinessRuleException("Only club owners can be assigned to clubs.");
        }
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found."));
        owner.getClubs().add(club);
    }
}
