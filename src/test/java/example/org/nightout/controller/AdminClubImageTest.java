package example.org.nightout.controller;

import example.org.nightout.entity.Club;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.storage.StorageFile;
import example.org.nightout.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminClubImageTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClubRepository clubRepository;

    @MockitoBean
    StorageService storageService;

    @BeforeEach
    void setUp() {
        clubRepository.deleteAll();
        reset(storageService);
        when(storageService.upload(any(byte[].class), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    byte[] content = invocation.getArgument(0);
                    String filename = invocation.getArgument(1);
                    String mimeType = invocation.getArgument(2);
                    String storagePrefix = invocation.getArgument(3);
                    return new StorageFile(storagePrefix + "/" + filename, filename, mimeType, content.length);
                });
    }

    @Test
    void createClubWithUploadedImageStoresImageMetadata() throws Exception {
        mockMvc.perform(multipart("/admin/clubs")
                        .file(clubImage("clubImage", "modular.jpg"))
                        .param("name", "Modular")
                        .param("slug", "modular")
                        .param("city", "Cape Town")
                        .param("area", "Claremont")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("successMessage", "Club created."));

        Club club = clubRepository.findBySlug("modular").orElseThrow();
        assertThat(club.getImageStorageFileId()).startsWith("clubs/modular/club-image/club-image-");
        assertThat(club.getImageStorageFileId()).endsWith(".jpg");
        assertThat(club.getImageMimeType()).isEqualTo("image/jpeg");
        assertThat(club.getImageFileSize()).isPositive();
        assertThat(club.getImageUploadedAt()).isNotNull();
    }

    @Test
    void editClubWithoutNewImageKeepsExistingImage() throws Exception {
        Club club = saveClubWithImage("HALO", "halo", "clubs/halo/club-image/old.jpg");

        mockMvc.perform(multipart("/admin/clubs/{clubId}", club.getId())
                        .param("name", "HALO")
                        .param("slug", "halo")
                        .param("city", "Cape Town")
                        .param("area", "Cape Town")
                        .param("active", "true")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        Club updated = clubRepository.findById(club.getId()).orElseThrow();
        assertThat(updated.getImageStorageFileId()).isEqualTo("clubs/halo/club-image/old.jpg");
        assertThat(updated.getImageMimeType()).isEqualTo("image/jpeg");
        assertThat(updated.getImageFileSize()).isEqualTo(123L);
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void editClubWithNewImageReplacesOldImageAndDeletesOldStorageObject() throws Exception {
        Club club = saveClubWithImage("HALO", "halo", "clubs/halo/club-image/old.jpg");

        mockMvc.perform(multipart("/admin/clubs/{clubId}", club.getId())
                        .file(clubImage("clubImage", "new-halo.jpg"))
                        .param("name", "HALO")
                        .param("slug", "halo")
                        .param("city", "Cape Town")
                        .param("area", "Cape Town")
                        .param("active", "true")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("successMessage", "Club updated."));

        Club updated = clubRepository.findById(club.getId()).orElseThrow();
        assertThat(updated.getImageStorageFileId()).startsWith("clubs/halo/club-image/club-image-");
        assertThat(updated.getImageStorageFileId()).isNotEqualTo("clubs/halo/club-image/old.jpg");
        verify(storageService).delete("clubs/halo/club-image/old.jpg");
    }

    @Test
    void invalidClubImageRedirectsBackWithError() throws Exception {
        Club club = saveClubWithImage("HALO", "halo", "clubs/halo/club-image/old.jpg");
        MockMultipartFile text = new MockMultipartFile("clubImage", "notes.txt", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/admin/clubs/{clubId}", club.getId())
                        .file(text)
                        .param("name", "HALO")
                        .param("slug", "halo")
                        .param("city", "Cape Town")
                        .param("area", "Cape Town")
                        .param("active", "true")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/clubs/" + club.getId() + "/edit"))
                .andExpect(flash().attribute("errorMessage", "Only JPEG, PNG, and WebP images can be uploaded."));

        Club updated = clubRepository.findById(club.getId()).orElseThrow();
        assertThat(updated.getImageStorageFileId()).isEqualTo("clubs/halo/club-image/old.jpg");
    }

    private Club saveClubWithImage(String name, String slug, String storageFileId) {
        Club club = new Club();
        club.setName(name);
        club.setSlug(slug);
        club.setCity("Cape Town");
        club.setArea("Cape Town");
        club.setActive(true);
        club.setImageStorageFileId(storageFileId);
        club.setImageMimeType("image/jpeg");
        club.setImageFileSize(123L);
        club.setImageUploadedAt(Instant.parse("2026-08-10T12:00:00Z"));
        return clubRepository.save(club);
    }

    private static MockMultipartFile clubImage(String fieldName, String filename) throws Exception {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(24, 169, 153));
        graphics.fillRect(0, 0, 120, 80);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return new MockMultipartFile(fieldName, filename, "image/jpeg", output.toByteArray());
    }
}
