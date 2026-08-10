package example.org.nightout.service;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.UserRole;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.ClubRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserManagementServiceTest {

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    ClubRepository clubRepository;

    Club ownedClub;
    Club otherClub;
    AppUser owner;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        clubRepository.deleteAll();

        ownedClub = clubRepository.save(club("Halo", "halo"));
        otherClub = clubRepository.save(club("Modular", "modular"));

        owner = new AppUser();
        owner.setEmail("owner@example.com");
        owner.setFullName("Owner");
        owner.setPasswordHash("hash");
        owner.setRole(UserRole.CLUB_OWNER);
        owner.getClubs().add(ownedClub);
        owner = userRepository.save(owner);
    }

    @Test
    void clubOwnerCanManageAssignedClub() {
        assertThatCode(() -> userManagementService.requireCanManageClub(owner.getId(), ownedClub.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void clubOwnerCannotModifyAnotherClub() {
        assertThatThrownBy(() -> userManagementService.requireCanManageClub(owner.getId(), otherClub.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You cannot manage this club.");
    }

    private Club club(String name, String slug) {
        Club club = new Club();
        club.setName(name);
        club.setSlug(slug);
        club.setCity("Cape Town");
        club.setActive(true);
        return club;
    }
}
