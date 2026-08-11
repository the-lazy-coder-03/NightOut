package example.org.nightout.service;

import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoOptimizationStatus;
import example.org.nightout.entity.PhotoStatus;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.repository.NightEventRepository;
import example.org.nightout.repository.PhotoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "nightout.image-optimization.enabled=true")
class PhotoServiceBusinessRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    @Autowired
    PhotoService photoService;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    NightEventRepository eventRepository;

    @Autowired
    PhotoRepository photoRepository;

    @MockitoBean
    ImageOptimizationService imageOptimizationService;

    Club halo;
    Club modular;
    NightEvent todayEvent;
    NightEvent recentEvent;
    NightEvent futureEvent;
    NightEvent expiredEvent;
    NightEvent modularEvent;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        clubRepository.deleteAll();
        reset(imageOptimizationService);

        halo = saveClub("HALO", "halo");
        modular = saveClub("Modular", "modular");
        todayEvent = saveEvent(halo, "Tonight", TODAY);
        recentEvent = saveEvent(halo, "Recent", TODAY.minusDays(2));
        futureEvent = saveEvent(halo, "Future", TODAY.plusDays(4));
        expiredEvent = saveEvent(halo, "Expired", TODAY.minusDays(8));
        modularEvent = saveEvent(modular, "Other Club", TODAY);
    }

    @Test
    void uploadToTodaysEventSucceeds() {
        List<Photo> photos = photoService.uploadPhotos("halo", todayEvent.getId(), new MockMultipartFile[]{jpeg("today.jpg")});

        assertThat(photos).hasSize(1);
        assertThat(photos.getFirst().getStatus()).isEqualTo(PhotoStatus.APPROVED);
        assertThat(photos.getFirst().getOptimizationStatus()).isEqualTo(PhotoOptimizationStatus.PENDING);
        verify(imageOptimizationService).enqueue(photos.getFirst().getId());
    }

    @Test
    void uploadToRecentEventSucceeds() {
        List<Photo> photos = photoService.uploadPhotos("halo", recentEvent.getId(), new MockMultipartFile[]{png("recent.png")});

        assertThat(photos).hasSize(1);
    }

    @Test
    void uploadToFutureEventFails() {
        assertThatThrownBy(() -> photoService.uploadPhotos("halo", futureEvent.getId(), new MockMultipartFile[]{jpeg("future.jpg")}))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This night has not happened yet.");
    }

    @Test
    void uploadToExpiredEventFails() {
        assertThatThrownBy(() -> photoService.uploadPhotos("halo", expiredEvent.getId(), new MockMultipartFile[]{jpeg("expired.jpg")}))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This gallery has expired.");
    }

    @Test
    void clubEventMismatchFails() {
        assertThatThrownBy(() -> photoService.uploadPhotos("halo", modularEvent.getId(), new MockMultipartFile[]{jpeg("wrong.jpg")}))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unsupportedFileTypeFails() {
        MockMultipartFile text = new MockMultipartFile("photos", "notes.txt", "text/plain", "not an image".getBytes());

        assertThatThrownBy(() -> photoService.uploadPhotos("halo", todayEvent.getId(), new MockMultipartFile[]{text}))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only JPEG, PNG, and WebP images can be uploaded.");
    }

    @Test
    void expiredPhotosAreSelectedForCleanup() {
        Photo expiredPhoto = new Photo();
        expiredPhoto.setEvent(expiredEvent);
        expiredPhoto.setOriginalFilename("old.jpg");
        expiredPhoto.setSafeFilename("old.jpg");
        expiredPhoto.setMimeType("image/jpeg");
        expiredPhoto.setFileSize(3);
        expiredPhoto.setStorageFileId("old-storage-id");
        photoRepository.save(expiredPhoto);

        assertThat(photoService.expiredPhotos())
                .extracting(Photo::getStorageFileId)
                .containsExactly("old-storage-id");
    }

    private Club saveClub(String name, String slug) {
        Club club = new Club();
        club.setName(name);
        club.setSlug(slug);
        club.setCity("Cape Town");
        club.setArea("Cape Town");
        club.setActive(true);
        return clubRepository.save(club);
    }

    private NightEvent saveEvent(Club club, String name, LocalDate date) {
        NightEvent event = new NightEvent();
        event.setClub(club);
        event.setEventName(name);
        event.setEventDate(date);
        event.setStartTime(LocalTime.of(21, 0));
        event.setEndTime(LocalTime.of(3, 0));
        return eventRepository.save(event);
    }

    private MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("photos", filename, "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
    }

    private MockMultipartFile png(String filename) {
        return new MockMultipartFile("photos", filename, "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-10T13:00:00Z"), ZoneId.of("UTC"));
        }
    }
}
