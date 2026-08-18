package example.org.nightout.controller.api;

import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoStatus;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.repository.NightEventRepository;
import example.org.nightout.repository.PhotoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicApiControllerTest {

    private static final String HALO_LOGO_URL = "https://festival101.co.za/wp-content/uploads/2023/11/Halo-Feature-image-blog-min.jpg";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    NightEventRepository eventRepository;

    @Autowired
    PhotoRepository photoRepository;

    Club halo;
    Club modular;
    NightEvent haloFriday;
    NightEvent modularFriday;
    Photo haloFridayPhoto;
    Photo modularFridayPhoto;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        clubRepository.deleteAll();

        halo = saveClub("HALO", "halo", "Cape Town", HALO_LOGO_URL);
        modular = saveClub("Modular", "modular", "Claremont");
        haloFriday = saveEvent(halo, "Friday Night", LocalDate.of(2026, 8, 7));
        modularFriday = saveEvent(modular, "Other Friday", LocalDate.of(2026, 8, 7));
        haloFridayPhoto = savePhoto(haloFriday, "halo-friday.jpg", PhotoStatus.APPROVED);
        modularFridayPhoto = savePhoto(modularFriday, "modular-friday.jpg", PhotoStatus.APPROVED);
    }

    @Test
    void areasReturnsPublicAreaList() throws Exception {
        mockMvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas", hasSize(3)))
                .andExpect(jsonPath("$.areas[0].displayName").value("Cape Town"))
                .andExpect(jsonPath("$.areas[0].slug").value("cape-town"))
                .andExpect(jsonPath("$.areas[1].slug").value("claremont"))
                .andExpect(jsonPath("$.areas[2].slug").value("stellenbosch"));
    }

    @Test
    void areaClubsReturnsOnlyActiveClubsForArea() throws Exception {
        mockMvc.perform(get("/api/v1/areas/cape-town/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area.slug").value("cape-town"))
                .andExpect(jsonPath("$.clubs", hasSize(1)))
                .andExpect(jsonPath("$.clubs[0].name").value("HALO"))
                .andExpect(jsonPath("$.clubs[0].slug").value("halo"))
                .andExpect(jsonPath("$.clubs[0].imageUrl").value(HALO_LOGO_URL));
    }

    @Test
    void areaClubsPreferUploadedClubImageUrl() throws Exception {
        halo.setImageStorageFileId("clubs/halo/club-image/halo.jpg");
        halo.setImageMimeType("image/jpeg");
        halo.setImageFileSize(123L);
        halo.setImageUploadedAt(Instant.parse("2026-08-07T20:00:00Z"));
        clubRepository.save(halo);

        mockMvc.perform(get("/api/v1/areas/cape-town/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubs[0].imageUrl").value("/club-images/" + halo.getId()));
    }

    @Test
    void clubReturnsRecentNightDatesAndEmbeddedEvents() throws Exception {
        mockMvc.perform(get("/api/v1/clubs/halo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.club.name").value("HALO"))
                .andExpect(jsonPath("$.nightDates", hasSize(8)))
                .andExpect(jsonPath("$.nightDates[0].date").value("2026-07-31"))
                .andExpect(jsonPath("$.nightDates[7].date").value("2026-08-07"))
                .andExpect(jsonPath("$.nightDates[7].current").value(true))
                .andExpect(jsonPath("$.nightDates[7].events[0].name").value("Friday Night"))
                .andExpect(jsonPath("$.nightDates[7].events[0].photoCount").value(1));
    }

    @Test
    void dateGalleryReturnsEventsUploadChoicesPhotosAndLimits() throws Exception {
        mockMvc.perform(get("/api/v1/clubs/halo/dates/2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.club.slug").value("halo"))
                .andExpect(jsonPath("$.nightDate").value("2026-08-07"))
                .andExpect(jsonPath("$.uploadAvailable").value(true))
                .andExpect(jsonPath("$.uploadLimits.maxUploadCount").value(12))
                .andExpect(jsonPath("$.uploadLimits.maxUploadBytes").value(26214400))
                .andExpect(jsonPath("$.uploadLimits.maxUploadBatchBytes").value(283115520))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.uploadEvents", hasSize(1)))
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].originalFilename").value("halo-friday.jpg"))
                .andExpect(jsonPath("$.photos[0].url").value("/photos/" + haloFridayPhoto.getId()))
                .andExpect(jsonPath("$.photos[0].downloadUrl").value("/photos/" + haloFridayPhoto.getId() + "/download"));
    }

    @Test
    void expiredDateGalleryReturnsUnavailableEventsWithoutPhotos() throws Exception {
        NightEvent expired = saveEvent(halo, "Expired", LocalDate.of(2026, 7, 30));
        savePhoto(expired, "expired-date.jpg", PhotoStatus.APPROVED);

        mockMvc.perform(get("/api/v1/clubs/halo/dates/2026-07-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadAvailable").value(false))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.events[0].galleryAvailable").value(false))
                .andExpect(jsonPath("$.uploadEvents", hasSize(0)))
                .andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    void eventGalleryReturnsEventAndPhotos() throws Exception {
        mockMvc.perform(get("/api/v1/clubs/halo/events/{eventId}/gallery", haloFriday.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.club.slug").value("halo"))
                .andExpect(jsonPath("$.event.name").value("Friday Night"))
                .andExpect(jsonPath("$.event.status").value("ACTIVE"))
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].safeFilename").value("halo-friday.jpg"));
    }

    @Test
    void dateUploadReturnsJsonAndCreatesDefaultEventWhenNeeded() throws Exception {
        mockMvc.perform(multipart("/api/v1/clubs/halo/dates/2026-08-06/upload")
                        .file(jpeg("mobile-date.jpg"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].originalFilename").value("mobile-date.jpg"));

        List<NightEvent> events = eventRepository.findByClubAndCancelledFalseAndEventDateOrderByStartTimeAsc(halo, LocalDate.of(2026, 8, 6));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventName()).isEqualTo("Night Out");
    }

    @Test
    void dateUploadCanTargetExistingEvent() throws Exception {
        mockMvc.perform(multipart("/api/v1/clubs/halo/dates/2026-08-07/upload")
                        .file(jpeg("mobile-date-event.jpg"))
                        .param("eventId", haloFriday.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.photos[0].originalFilename").value("mobile-date-event.jpg"));

        List<NightEvent> events = eventRepository.findByClubAndCancelledFalseAndEventDateOrderByStartTimeAsc(halo, LocalDate.of(2026, 8, 7));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getId()).isEqualTo(haloFriday.getId());
    }

    @Test
    void eventUploadReturnsJsonAndUsesExistingEvent() throws Exception {
        mockMvc.perform(multipart("/api/v1/clubs/halo/events/{eventId}/upload", haloFriday.getId())
                        .file(jpeg("mobile-event.jpg"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.photos[0].url").exists());
    }

    @Test
    void uploadValidationFailureReturnsJsonError() throws Exception {
        MockMultipartFile text = new MockMultipartFile("photos", "notes.txt", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/clubs/halo/dates/2026-08-06/upload")
                        .file(text)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG, and WebP images can be uploaded."))
                .andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    void unknownClubReturnsJsonNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/clubs/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Club not found."));
    }

    @Test
    void unknownAreaReturnsJsonNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/areas/johannesburg/clubs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Area not found."));
    }

    @Test
    void unknownUploadTargetReturnsUploadJsonNotFound() throws Exception {
        mockMvc.perform(multipart("/api/v1/clubs/halo/events/999999/upload")
                        .file(jpeg("missing.jpg"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.message").value("Night not found."))
                .andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    void expiredEventGalleryReturnsJsonBusinessError() throws Exception {
        NightEvent expired = saveEvent(halo, "Expired", LocalDate.of(2026, 7, 30));
        savePhoto(expired, "expired.jpg", PhotoStatus.APPROVED);

        mockMvc.perform(get("/api/v1/clubs/halo/events/{eventId}/gallery", expired.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This gallery has expired."));
    }

    @Test
    void unknownEventReturnsJsonNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/clubs/halo/events/999999/gallery"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Night not found."));
    }

    private Club saveClub(String name, String slug, String area) {
        return saveClub(name, slug, area, null);
    }

    private Club saveClub(String name, String slug, String area, String logoUrl) {
        Club club = new Club();
        club.setName(name);
        club.setSlug(slug);
        club.setCity("Cape Town");
        club.setArea(area);
        club.setLogoUrl(logoUrl);
        club.setActive(true);
        return clubRepository.save(club);
    }

    private NightEvent saveEvent(Club club, String name, LocalDate date) {
        NightEvent event = new NightEvent();
        event.setClub(club);
        event.setEventName(name);
        event.setEventDate(date);
        event.setStartTime(LocalTime.of(22, 0));
        event.setEndTime(LocalTime.of(3, 0));
        return eventRepository.save(event);
    }

    private Photo savePhoto(NightEvent event, String filename, PhotoStatus status) {
        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setOriginalFilename(filename);
        photo.setSafeFilename(filename);
        photo.setMimeType("image/jpeg");
        photo.setFileSize(4);
        photo.setStorageFileId(filename);
        photo.setStatus(status);
        return photoRepository.save(photo);
    }

    private MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("photos", filename, "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-08T03:00:00Z"), ZoneId.of("UTC"));
        }
    }
}
