package example.org.nightout.service;

import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoOptimizationStatus;
import example.org.nightout.entity.PhotoStatus;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.repository.NightEventRepository;
import example.org.nightout.repository.PhotoRepository;
import example.org.nightout.storage.StorageFile;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "nightout.image-optimization.enabled=true",
        "nightout.image-optimization.initial-delay-ms=3600000"
})
class ImageOptimizationServiceTest {

    @Autowired
    ImageOptimizationService imageOptimizationService;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    NightEventRepository eventRepository;

    @Autowired
    PhotoRepository photoRepository;

    @MockitoBean
    StorageService storageService;

    Club halo;
    NightEvent event;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        clubRepository.deleteAll();
        reset(storageService);

        halo = saveClub();
        event = saveEvent(halo);
    }

    @Test
    void optimizationUpdatesPhotoTo1080JpegAndDeletesOriginal() throws Exception {
        byte[] originalBytes = jpeg(2200, 1100);
        Photo photo = savePendingPhoto("clubs/halo/2026-08-10/original.jpg", "original.jpg", originalBytes.length);
        when(storageService.retrieve(photo.getStorageFileId()))
                .thenReturn(new StorageResource(new ByteArrayResource(originalBytes), originalBytes.length));
        when(storageService.upload(
                any(byte[].class),
                argThat(filename -> filename.startsWith("optimized-" + photo.getId()) && filename.endsWith(".jpg")),
                eq("image/jpeg"),
                eq("clubs/halo/2026-08-10")
        )).thenAnswer(invocation -> {
            byte[] content = invocation.getArgument(0);
            String filename = invocation.getArgument(1);
            return new StorageFile("clubs/halo/2026-08-10/" + filename, filename, "image/jpeg", content.length);
        });

        imageOptimizationService.optimizeNow(photo.getId());

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertThat(updated.getOptimizationStatus()).isEqualTo(PhotoOptimizationStatus.COMPLETE);
        assertThat(updated.getStorageFileId()).startsWith("clubs/halo/2026-08-10/optimized-" + photo.getId());
        assertThat(updated.getMimeType()).isEqualTo("image/jpeg");
        assertThat(updated.getOptimizedAt()).isNotNull();

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).upload(contentCaptor.capture(), any(String.class), eq("image/jpeg"), eq("clubs/halo/2026-08-10"));
        BufferedImage optimizedImage = ImageIO.read(new ByteArrayInputStream(contentCaptor.getValue()));
        assertThat(optimizedImage.getWidth()).isEqualTo(1080);
        assertThat(optimizedImage.getHeight()).isEqualTo(540);
        verify(storageService).delete("clubs/halo/2026-08-10/original.jpg");
    }

    @Test
    void optimizationFailureKeepsOriginalVisibleAndMarksFailed() {
        Photo photo = savePendingPhoto("clubs/halo/2026-08-10/broken.jpg", "broken.jpg", 8);
        when(storageService.retrieve(photo.getStorageFileId()))
                .thenReturn(new StorageResource(new ByteArrayResource("notimage".getBytes()), 8));

        imageOptimizationService.optimizeNow(photo.getId());

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertThat(updated.getStorageFileId()).isEqualTo("clubs/halo/2026-08-10/broken.jpg");
        assertThat(updated.getOptimizationStatus()).isEqualTo(PhotoOptimizationStatus.FAILED);
        assertThat(updated.getOptimizationError()).contains("Could not read image");
        verify(storageService, never()).delete("clubs/halo/2026-08-10/broken.jpg");
        verify(storageService, never()).upload(any(byte[].class), any(String.class), any(String.class), any(String.class));
    }

    private Club saveClub() {
        Club club = new Club();
        club.setName("HALO");
        club.setSlug("halo");
        club.setCity("Cape Town");
        club.setArea("Cape Town");
        club.setActive(true);
        return clubRepository.save(club);
    }

    private NightEvent saveEvent(Club club) {
        NightEvent nightEvent = new NightEvent();
        nightEvent.setClub(club);
        nightEvent.setEventName("Tonight");
        nightEvent.setEventDate(LocalDate.of(2026, 8, 10));
        nightEvent.setStartTime(LocalTime.of(22, 0));
        nightEvent.setEndTime(LocalTime.of(3, 0));
        return eventRepository.save(nightEvent);
    }

    private Photo savePendingPhoto(String storageFileId, String filename, long fileSize) {
        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setStorageFileId(storageFileId);
        photo.setOriginalFilename(filename);
        photo.setSafeFilename(filename);
        photo.setMimeType("image/jpeg");
        photo.setFileSize(fileSize);
        photo.setStatus(PhotoStatus.APPROVED);
        photo.setOptimizationStatus(PhotoOptimizationStatus.PENDING);
        return photoRepository.save(photo);
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(24, 120, 104));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
