package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.ClubRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class DemoDataLoader implements CommandLineRunner {

    private final AppProperties properties;
    private final ClubRepository clubRepository;
    private final AppUserRepository userRepository;
    private final ClubService clubService;
    private final EventService eventService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataLoader(AppProperties properties, ClubRepository clubRepository, AppUserRepository userRepository, ClubService clubService, EventService eventService, PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.clubService = clubService;
        this.eventService = eventService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.isSeedDemo() || clubRepository.count() > 0) {
            return;
        }

        Club halo = clubService.create("Halo", "halo", "Cape Town", "Cape Town", "12 Loop Street", null, null, true);
        Club modular = clubService.create("Modular", "modular", "Cape Town", "Claremont", "38 Harrington Street", null, null, true);
        clubService.create("Club Paradise", "club-paradise", "Cape Town", "Stellenbosch", "99 Bree Street", null, null, true);

        LocalDate today = LocalDate.now(clock);
        for (Club club : List.of(halo, modular)) {
            eventService.create(club.getId(), "Tonight", today, LocalTime.of(21, 0), LocalTime.of(3, 0));
            eventService.create(club.getId(), "Last Weekend", today.minusDays(2), LocalTime.of(21, 0), LocalTime.of(3, 0));
            eventService.create(club.getId(), "Launch Night", today.minusDays(10), LocalTime.of(21, 0), LocalTime.of(3, 0));
            eventService.create(club.getId(), "Next Saturday", today.plusDays(5), LocalTime.of(21, 0), LocalTime.of(3, 0));
        }

        AppUser admin = new AppUser();
        admin.setEmail("admin@nightout.local");
        admin.setFullName("Nightout Admin");
        admin.setPasswordHash(passwordEncoder.encode("admin12345"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        AppUser owner = new AppUser();
        owner.setEmail("owner@nightout.local");
        owner.setFullName("Halo Owner");
        owner.setPasswordHash(passwordEncoder.encode("owner12345"));
        owner.setRole(UserRole.CLUB_OWNER);
        owner.getClubs().add(halo);
        userRepository.save(owner);
    }
}
