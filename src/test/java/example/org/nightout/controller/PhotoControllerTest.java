package example.org.nightout.controller;

import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoOptimizationStatus;
import example.org.nightout.entity.PhotoStatus;
import example.org.nightout.service.PhotoService;
import example.org.nightout.storage.StorageResource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhotoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PhotoService photoService;

    @Test
    void optimizedPhotoIsServedInlineWithLongCacheHeaders() throws Exception {
        Photo photo = photo(42L, PhotoOptimizationStatus.COMPLETE, 1234);
        byte[] body = new byte[]{1, 2, 3};
        when(photoService.publicPhoto(42L)).thenReturn(photo);
        when(photoService.retrieve(photo)).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        mockMvc.perform(get("/photos/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Cache-Control", containsString("max-age=604800")))
                .andExpect(header().string("ETag", startsWith("\"photo-42-1234-COMPLETE")));
    }

    @Test
    void pendingPhotoRevalidatesSoOptimizedSwapCanBeSeen() throws Exception {
        Photo photo = photo(43L, PhotoOptimizationStatus.PENDING, 4567);
        byte[] body = new byte[]{1, 2, 3};
        when(photoService.publicPhoto(43L)).thenReturn(photo);
        when(photoService.retrieve(photo)).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        mockMvc.perform(get("/photos/43"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("ETag", startsWith("\"photo-43-4567-PENDING")));
    }

    @Test
    void photoDownloadIsServedAsAttachment() throws Exception {
        Photo photo = photo(44L, PhotoOptimizationStatus.COMPLETE, 7890);
        byte[] body = new byte[]{1, 2, 3};
        when(photoService.publicPhoto(44L)).thenReturn(photo);
        when(photoService.retrieve(photo)).thenReturn(new StorageResource(new ByteArrayResource(body), body.length));

        mockMvc.perform(get("/photos/44/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("photo.jpg")));
    }

    @Test
    void selectedPhotosDownloadAsZipArchive() throws Exception {
        Photo first = photo(45L, PhotoOptimizationStatus.COMPLETE, 3);
        Photo second = photo(46L, PhotoOptimizationStatus.COMPLETE, 4);
        second.setStorageFileId("second.jpg");
        second.setSafeFilename("photo.jpg");
        when(photoService.publicPhoto(45L)).thenReturn(first);
        when(photoService.publicPhoto(46L)).thenReturn(second);
        when(photoService.retrieve(first)).thenReturn(new StorageResource(new ByteArrayResource(new byte[]{1, 2, 3}), 3));
        when(photoService.retrieve(second)).thenReturn(new StorageResource(new ByteArrayResource(new byte[]{4, 5, 6, 7}), 4));

        MvcResult result = mockMvc.perform(get("/photos/download")
                        .param("photoIds", "45", "46", "45"))
                .andExpect(request().asyncStarted())
                .andReturn();

        byte[] body = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("nightout-photos.zip")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(zipEntryNames(body)).containsExactly("photo.jpg", "photo-2.jpg");
    }

    private static List<String> zipEntryNames(byte[] body) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                zip.closeEntry();
            }
        }
        return names;
    }

    private static Photo photo(Long id, PhotoOptimizationStatus optimizationStatus, long fileSize) {
        Photo photo = new Photo();
        ReflectionTestUtils.setField(photo, "id", id);
        photo.setOriginalFilename("photo.jpg");
        photo.setSafeFilename("photo.jpg");
        photo.setMimeType("image/jpeg");
        photo.setFileSize(fileSize);
        photo.setStatus(PhotoStatus.APPROVED);
        photo.setStorageFileId("photo.jpg");
        photo.setOptimizationStatus(optimizationStatus);
        photo.setUploadedAt(Instant.parse("2026-08-10T12:00:00Z"));
        if (optimizationStatus == PhotoOptimizationStatus.COMPLETE) {
            photo.setOptimizedAt(Instant.parse("2026-08-10T12:01:00Z"));
        }
        return photo;
    }
}
