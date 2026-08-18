package example.org.nightout.controller;

import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.exception.StorageException;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.repository.NightEventRepository;
import example.org.nightout.repository.PhotoRepository;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPhotoBulkDeleteTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    NightEventRepository eventRepository;

    @Autowired
    PhotoRepository photoRepository;

    @MockitoBean
    StorageService storageService;

    Club club;
    NightEvent event;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        clubRepository.deleteAll();
        reset(storageService);

        club = saveClub();
        event = saveEvent(club);
    }

    @Test
    void adminDashboardShowsDeleteAllPhotosButtonWhenPhotosExist() throws Exception {
        Photo photo = savePhoto("one.jpg");

        mockMvc.perform(get("/admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Delete All Photos")))
                .andExpect(content().string(containsString("action=\"/admin/photos/delete-all\"")))
                .andExpect(content().string(containsString("href=\"/admin/photos/" + photo.getId() + "/download\"")))
                .andExpect(content().string(containsString("data-bulk-save")))
                .andExpect(content().string(containsString("data-bulk-download-url=\"/admin/photos/download\"")))
                .andExpect(content().string(containsString("data-photo-select")))
                .andExpect(content().string(containsString("data-share-url=\"/admin/photos/" + photo.getId() + "/download\"")))
                .andExpect(content().string(containsString("/js/photo-save.js")));
    }

    @Test
    void adminCanDownloadAnyListedPhoto() throws Exception {
        Photo photo = savePhoto("one.jpg");
        byte[] body = new byte[]{1, 2, 3};
        when(storageService.retrieve("one.jpg")).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        mockMvc.perform(get("/admin/photos/{photoId}/download", photo.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("one.jpg")));
    }

    @Test
    void adminCanDownloadSelectedPhotosAsZip() throws Exception {
        Photo photo = savePhoto("one.jpg");
        byte[] body = new byte[]{1, 2, 3};
        when(storageService.retrieve("one.jpg")).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        MvcResult result = mockMvc.perform(get("/admin/photos/download")
                        .param("photoIds", String.valueOf(photo.getId()))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("crowdcam-photos.zip")));
    }

    @Test
    void deleteAllPhotosRemovesEveryStorageObjectAndDatabaseRecord() throws Exception {
        savePhoto("one.jpg");
        savePhoto("two.jpg");

        mockMvc.perform(post("/admin/photos/delete-all")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("successMessage", "2 photos deleted."));

        assertThat(photoRepository.count()).isZero();
        verify(storageService).delete("one.jpg");
        verify(storageService).delete("two.jpg");
    }

    @Test
    void deleteAllPhotosKeepsRowsWhoseStorageDeleteFails() throws Exception {
        savePhoto("one.jpg");
        savePhoto("two.jpg");
        doThrow(new StorageException("delete failed")).when(storageService).delete("two.jpg");

        mockMvc.perform(post("/admin/photos/delete-all")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("errorMessage", "1 photo deleted. 1 photo could not be deleted and remain listed."));

        assertThat(photoRepository.findAll())
                .extracting(Photo::getStorageFileId)
                .containsExactly("two.jpg");
        verify(storageService).delete("one.jpg");
        verify(storageService).delete("two.jpg");
    }

    private Club saveClub() {
        Club savedClub = new Club();
        savedClub.setName("HALO");
        savedClub.setSlug("halo");
        savedClub.setCity("Cape Town");
        savedClub.setArea("Cape Town");
        savedClub.setActive(true);
        return clubRepository.save(savedClub);
    }

    private NightEvent saveEvent(Club savedClub) {
        NightEvent savedEvent = new NightEvent();
        savedEvent.setClub(savedClub);
        savedEvent.setEventName("Friday");
        savedEvent.setEventDate(LocalDate.of(2026, 8, 7));
        savedEvent.setStartTime(LocalTime.of(22, 0));
        savedEvent.setEndTime(LocalTime.of(3, 0));
        return eventRepository.save(savedEvent);
    }

    private Photo savePhoto(String storageFileId) {
        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setOriginalFilename(storageFileId);
        photo.setSafeFilename(storageFileId);
        photo.setMimeType("image/jpeg");
        photo.setFileSize(4);
        photo.setStorageFileId(storageFileId);
        return photoRepository.save(photo);
    }
}
