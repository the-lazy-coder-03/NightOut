package example.org.nightout.controller;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicDateFlowTest {

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

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        clubRepository.deleteAll();

        halo = saveClub("HALO", "halo", "Cape Town", HALO_LOGO_URL);
        modular = saveClub("Modular", "modular", "Claremont");
        haloFriday = saveEvent(halo, "Friday Night", LocalDate.of(2026, 8, 7));
        modularFriday = saveEvent(modular, "Other Friday", LocalDate.of(2026, 8, 7));
        savePhoto(haloFriday, "halo-friday.jpg", PhotoStatus.APPROVED);
        savePhoto(modularFriday, "modular-friday.jpg", PhotoStatus.APPROVED);
    }

    @Test
    void homePageShowsFixedAreaLinksInsteadOfClubCards() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NightOut South Africa")))
                .andExpect(content().string(containsString("/areas/cape-town")))
                .andExpect(content().string(containsString("/areas/claremont")))
                .andExpect(content().string(containsString("/areas/stellenbosch")))
                .andExpect(content().string(not(containsString("/clubs/halo"))))
                .andExpect(content().string(not(containsString("Friday Night"))));
    }

    @Test
    void areaPageShowsOnlyClubsForSelectedArea() throws Exception {
        mockMvc.perform(get("/areas/cape-town"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cape Town")))
                .andExpect(content().string(containsString("/clubs/halo")))
                .andExpect(content().string(containsString("class=\"club-card-image\"")))
                .andExpect(content().string(containsString(HALO_LOGO_URL)))
                .andExpect(content().string(containsString("HALO")))
                .andExpect(content().string(not(containsString("Modular"))));
    }

    @Test
    void unknownAreaReturnsNotFound() throws Exception {
        mockMvc.perform(get("/areas/johannesburg"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Area not found.")));
    }

    @Test
    void clubPageShowsCurrentNightAndSevenPreviousDatesBeforeNoon() throws Exception {
        String html = mockMvc.perform(get("/clubs/halo"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"club-hero-image\""))))
                .andExpect(content().string(containsString("/clubs/halo/dates/2026-08-07")))
                .andExpect(content().string(containsString("/clubs/halo/dates/2026-07-31")))
                .andExpect(content().string(not(containsString("/clubs/halo/dates/2026-08-08"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).containsSubsequence(
                "/clubs/halo/dates/2026-07-31",
                "/clubs/halo/dates/2026-08-07"
        );
    }

    @Test
    void dateGalleryShowsOnlySelectedClubAndNightDatePhotos() throws Exception {
        mockMvc.perform(get("/clubs/halo/dates/2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("halo-friday.jpg")))
                .andExpect(content().string(not(containsString("modular-friday.jpg"))));
    }

    @Test
    void dateGalleryShowsUploadButtonAbovePhotosAndPromptChoices() throws Exception {
        String html = mockMvc.perform(get("/clubs/halo/dates/2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-upload-dialog-open")))
                .andExpect(content().string(containsString("Take Photo")))
                .andExpect(content().string(containsString("Upload From Device")))
                .andExpect(content().string(containsString("capture=\"environment\"")))
                .andExpect(content().string(containsString("accept=\"image/jpeg,image/png,image/webp\"")))
                .andExpect(content().string(containsString("multiple")))
                .andExpect(content().string(containsString("action=\"/clubs/halo/dates/2026-08-07/upload\"")))
                .andExpect(content().string(containsString("name=\"eventId\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).containsSubsequence(
                "data-upload-dialog-open",
                "class=\"gallery\""
        );
    }

    @Test
    void dateGalleryWithNoPicturesRendersEmptyState() throws Exception {
        mockMvc.perform(get("/clubs/halo/dates/2026-08-06"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("No pictures"))))
                .andExpect(content().string(containsString("data-upload-dialog-open")))
                .andExpect(content().string(containsString("action=\"/clubs/halo/dates/2026-08-06/upload\"")))
                .andExpect(content().string(not(containsString("name=\"eventId\""))));
    }

    @Test
    void datePageUploadRedirectsBackToDateGalleryAndCreatesUploadTargetOnSuccess() throws Exception {
        mockMvc.perform(multipart("/clubs/halo/dates/2026-08-06/upload")
                        .file(jpeg("inline.jpg"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/dates/2026-08-06"))
                .andExpect(flash().attribute("successMessage", "1 photo uploaded successfully."));

        List<NightEvent> events = eventRepository.findByClubAndCancelledFalseAndEventDateOrderByStartTimeAsc(halo, LocalDate.of(2026, 8, 6));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventName()).isEqualTo("Night Out");
    }

    @Test
    void datePageUploadRedirectsBackToDateGalleryOnFailure() throws Exception {
        MockMultipartFile text = new MockMultipartFile("photos", "notes.txt", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/clubs/halo/dates/2026-08-06/upload")
                        .file(text)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/dates/2026-08-06"))
                .andExpect(flash().attribute("errorMessage", "Only JPEG, PNG, and WebP images can be uploaded."));
    }

    @Test
    void datePageUploadDoesNotRequireCsrf() throws Exception {
        MockMultipartFile text = new MockMultipartFile("photos", "notes.txt", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/clubs/halo/dates/2026-08-06/upload")
                        .file(text))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/dates/2026-08-06"))
                .andExpect(flash().attribute("errorMessage", "Only JPEG, PNG, and WebP images can be uploaded."));
    }

    @Test
    void emptyDatePageUploadRedirectsBackWithError() throws Exception {
        mockMvc.perform(post("/clubs/halo/dates/2026-08-07/upload")
                        .param("eventId", haloFriday.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/dates/2026-08-07"))
                .andExpect(flash().attribute("errorMessage", "Choose at least one image to upload."));
    }

    @Test
    void eventUploadCanStillReturnToDateGalleryWhenRequested() throws Exception {
        mockMvc.perform(multipart("/clubs/halo/events/{eventId}/upload", haloFriday.getId())
                        .file(jpeg("inline-event.jpg"))
                        .param("returnDate", "2026-08-07")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/dates/2026-08-07"))
                .andExpect(flash().attribute("successMessage", "1 photo uploaded successfully."));
    }

    @Test
    void eventPageUploadRedirectBehaviorIsUnchanged() throws Exception {
        mockMvc.perform(multipart("/clubs/halo/events/{eventId}/upload", haloFriday.getId())
                        .file(jpeg("event.jpg"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/events/" + haloFriday.getId() + "/gallery"));
    }

    @Test
    void eventPageUploadDoesNotRequireCsrf() throws Exception {
        mockMvc.perform(multipart("/clubs/halo/events/{eventId}/upload", haloFriday.getId())
                        .file(jpeg("event-no-csrf.jpg")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/events/" + haloFriday.getId() + "/gallery"));
    }

    @Test
    void emptyEventPageUploadRedirectsBackWithError() throws Exception {
        mockMvc.perform(post("/clubs/halo/events/{eventId}/upload", haloFriday.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/halo/events/" + haloFriday.getId() + "/upload"))
                .andExpect(flash().attribute("errorMessage", "Choose at least one image to upload."));
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
