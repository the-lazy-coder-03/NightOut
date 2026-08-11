package example.org.nightout.controller;

import example.org.nightout.entity.Club;
import example.org.nightout.service.ClubImageService;
import example.org.nightout.service.ClubService;
import example.org.nightout.storage.StorageResource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClubImageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ClubService clubService;

    @MockitoBean
    ClubImageService clubImageService;

    @Test
    void uploadedClubImageIsServedInlineWithCacheHeaders() throws Exception {
        Club club = uploadedClub();
        byte[] body = new byte[]{1, 2, 3};
        when(clubService.requireById(42L)).thenReturn(club);
        when(clubImageService.retrieve(club)).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        mockMvc.perform(get("/club-images/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")))
                .andExpect(header().string("ETag", containsString("club-image-42-1234")));
    }

    @Test
    void clubWithoutUploadedImageReturnsNotFound() throws Exception {
        Club club = new Club();
        ReflectionTestUtils.setField(club, "id", 43L);
        club.setName("Modular");
        club.setSlug("modular");
        when(clubService.requireById(43L)).thenReturn(club);

        mockMvc.perform(get("/club-images/43"))
                .andExpect(status().isNotFound());
    }

    private static Club uploadedClub() {
        Club club = new Club();
        ReflectionTestUtils.setField(club, "id", 42L);
        club.setName("HALO");
        club.setSlug("halo");
        club.setImageStorageFileId("clubs/halo/club-image/halo.jpg");
        club.setImageMimeType("image/jpeg");
        club.setImageFileSize(1234L);
        club.setImageUploadedAt(Instant.parse("2026-08-10T12:00:00Z"));
        return club;
    }
}
