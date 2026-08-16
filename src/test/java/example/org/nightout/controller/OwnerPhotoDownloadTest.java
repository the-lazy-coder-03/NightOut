package example.org.nightout.controller;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.repository.NightEventRepository;
import example.org.nightout.repository.PhotoRepository;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OwnerPhotoDownloadTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    NightEventRepository eventRepository;

    @Autowired
    PhotoRepository photoRepository;

    @Autowired
    AppUserRepository userRepository;

    @MockitoBean
    StorageService storageService;

    AppUser owner;
    Club ownedClub;
    Photo ownedPhoto;
    Photo otherPhoto;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
        clubRepository.deleteAll();
        reset(storageService);

        ownedClub = clubRepository.save(club("HALO", "halo"));
        Club otherClub = clubRepository.save(club("Modular", "modular"));
        owner = owner(ownedClub);
        ownedPhoto = photo(event(ownedClub), "owned.jpg");
        otherPhoto = photo(event(otherClub), "other.jpg");
    }

    @Test
    void ownerClubPageShowsProtectedDownloadLink() throws Exception {
        mockMvc.perform(get("/owner/clubs/{clubId}", ownedClub.getId())
                        .with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/owner/photos/" + ownedPhoto.getId() + "/download\"")))
                .andExpect(content().string(containsString("data-bulk-save")))
                .andExpect(content().string(containsString("data-bulk-download-url=\"/owner/photos/download\"")))
                .andExpect(content().string(containsString("data-photo-select")))
                .andExpect(content().string(containsString("data-share-url=\"/owner/photos/" + ownedPhoto.getId() + "/download\"")))
                .andExpect(content().string(containsString("/js/photo-save.js")));
    }

    @Test
    void ownerCanDownloadAssignedClubPhoto() throws Exception {
        byte[] body = new byte[]{1, 2, 3};
        when(storageService.retrieve("owned.jpg")).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        mockMvc.perform(get("/owner/photos/{photoId}/download", ownedPhoto.getId())
                        .with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("owned.jpg")));
    }

    @Test
    void ownerCanDownloadSelectedAssignedClubPhotosAsZip() throws Exception {
        byte[] body = new byte[]{1, 2, 3};
        when(storageService.retrieve("owned.jpg")).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        MvcResult result = mockMvc.perform(get("/owner/photos/download")
                        .param("photoIds", String.valueOf(ownedPhoto.getId()))
                        .with(auth(owner)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("nightout-photos.zip")));
    }

    @Test
    void ownerCannotDownloadAnotherClubPhoto() throws Exception {
        mockMvc.perform(get("/owner/photos/{photoId}/download", otherPhoto.getId())
                        .with(auth(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("You cannot manage this club.")));
    }

    private AppUser owner(Club club) {
        AppUser user = new AppUser();
        user.setEmail("owner@example.com");
        user.setFullName("Owner");
        user.setRole(UserRole.CLUB_OWNER);
        user.setLogtoSubject("logto-owner");
        user.getClubs().add(club);
        return userRepository.save(user);
    }

    private Club club(String name, String slug) {
        Club club = new Club();
        club.setName(name);
        club.setSlug(slug);
        club.setCity("Cape Town");
        club.setArea("Cape Town");
        club.setActive(true);
        return club;
    }

    private NightEvent event(Club club) {
        NightEvent event = new NightEvent();
        event.setClub(club);
        event.setEventName("Friday");
        event.setEventDate(LocalDate.of(2026, 8, 7));
        event.setStartTime(LocalTime.of(22, 0));
        event.setEndTime(LocalTime.of(3, 0));
        return eventRepository.save(event);
    }

    private Photo photo(NightEvent event, String storageFileId) {
        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setOriginalFilename(storageFileId);
        photo.setSafeFilename(storageFileId);
        photo.setMimeType("image/jpeg");
        photo.setFileSize(3);
        photo.setStorageFileId(storageFileId);
        return photoRepository.save(photo);
    }

    private static RequestPostProcessor auth(AppUser user) {
        AuthenticatedUser principal = new AuthenticatedUser(
                user,
                null,
                List.of("ROLE_USER", "ROLE_CLUB_OWNER").stream().map(SimpleGrantedAuthority::new).toList()
        );
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities())
        );
    }
}
