package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoStatus;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.repository.PhotoRepository;
import example.org.nightout.storage.StorageFile;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final EventService eventService;
    private final EventPolicyService policyService;
    private final StorageService storageService;
    private final AppProperties properties;

    public PhotoService(PhotoRepository photoRepository, EventService eventService, EventPolicyService policyService, StorageService storageService, AppProperties properties) {
        this.photoRepository = photoRepository;
        this.eventService = eventService;
        this.policyService = policyService;
        this.storageService = storageService;
        this.properties = properties;
    }

    @Transactional
    public List<Photo> uploadPhotos(String clubSlug, Long eventId, MultipartFile[] files) {
        NightEvent event = eventService.requirePublicEvent(clubSlug, eventId);
        return uploadPhotos(event, files);
    }

    @Transactional
    public List<Photo> uploadPhotosForDate(String clubSlug, LocalDate date, Long eventId, MultipartFile[] files) {
        NightEvent event = eventService.uploadTargetForClubDate(clubSlug, date, eventId);
        return uploadPhotos(event, files);
    }

    private List<Photo> uploadPhotos(NightEvent event, MultipartFile[] files) {
        policyService.requireUploadAvailable(event);

        List<MultipartFile> submittedFiles = Arrays.stream(files == null ? new MultipartFile[0] : files)
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (submittedFiles.isEmpty()) {
            throw new BusinessRuleException("Choose at least one image to upload.");
        }
        if (submittedFiles.size() > properties.getMaxUploadCount()) {
            throw new BusinessRuleException("You can upload up to " + properties.getMaxUploadCount() + " photos at once.");
        }

        return submittedFiles.stream()
                .map(file -> uploadOne(event, file))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Photo> galleryPhotos(String clubSlug, Long eventId) {
        NightEvent event = eventService.requirePublicEvent(clubSlug, eventId);
        policyService.requireGalleryAvailable(event);
        return photoRepository.findByEventAndStatusOrderByUploadedAtDesc(event, PhotoStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<Photo> galleryPhotosForEvents(List<NightEvent> events) {
        List<NightEvent> availableEvents = events.stream()
                .filter(policyService::galleryAvailable)
                .toList();
        if (availableEvents.isEmpty()) {
            return List.of();
        }
        return photoRepository.findByEventInAndStatusOrderByUploadedAtDesc(availableEvents, PhotoStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public Photo publicPhoto(Long photoId) {
        Photo photo = photoRepository.findWithEventById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found."));
        if (photo.getStatus() != PhotoStatus.APPROVED) {
            throw new ResourceNotFoundException("Photo not found.");
        }
        policyService.requireGalleryAvailable(photo.getEvent());
        return photo;
    }

    public StorageResource retrieve(Photo photo) {
        return storageService.retrieve(photo.getStorageFileId());
    }

    @Transactional(readOnly = true)
    public Long clubIdForPhoto(Long photoId) {
        Photo photo = photoRepository.findWithEventById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found."));
        return photo.getEvent().getClub().getId();
    }

    @Transactional
    public void removePhoto(Long photoId) {
        Photo photo = photoRepository.findWithEventById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found."));
        storageService.delete(photo.getStorageFileId());
        photoRepository.delete(photo);
    }

    @Transactional(readOnly = true)
    public List<Photo> expiredPhotos() {
        return photoRepository.findByEvent_EventDateBefore(policyService.expiredCutoffDate());
    }

    @Transactional
    public void deleteRecord(Photo photo) {
        photoRepository.deleteById(photo.getId());
    }

    private Photo uploadOne(NightEvent event, MultipartFile multipartFile) {
        if (multipartFile.getSize() > properties.getMaxUploadBytes()) {
            throw new BusinessRuleException("Each image must be smaller than " + Math.max(1, properties.getMaxUploadBytes() / 1024 / 1024) + " MB.");
        }
        byte[] bytes;
        try {
            bytes = multipartFile.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("Could not read the uploaded image.");
        }
        ImageType imageType = detectImageType(bytes, multipartFile.getOriginalFilename(), multipartFile.getContentType());
        String originalFilename = safeOriginalFilename(multipartFile.getOriginalFilename());
        String safeFilename = event.getClub().getSlug() + "-" + event.getEventDate() + "-" + UUID.randomUUID() + "." + imageType.extension();
        StorageFile stored = storageService.upload(bytes, safeFilename, imageType.mimeType(), event.getClub().getStorageFolderId());

        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setOriginalFilename(originalFilename);
        photo.setSafeFilename(safeFilename);
        photo.setMimeType(stored.mimeType());
        photo.setFileSize(stored.sizeBytes());
        photo.setStorageFileId(stored.id());
        photo.setStatus(PhotoStatus.APPROVED);
        photo.setUploadedAt(Instant.now());
        return photoRepository.save(photo);
    }

    private static ImageType detectImageType(byte[] bytes, String filename, String contentType) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".heic") || lowerName.endsWith(".heif") || lowerContentType.contains("heic") || lowerContentType.contains("heif")) {
            throw new BusinessRuleException("HEIC/HEIF photos are not supported yet. Please upload JPEG, PNG, or WebP.");
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return ImageType.JPEG;
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return ImageType.PNG;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return ImageType.WEBP;
        }
        throw new BusinessRuleException("Only JPEG, PNG, and WebP images can be uploaded.");
    }

    private static String safeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "photo";
        }
        String normalized = originalFilename.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String mimeType;
        private final String extension;

        ImageType(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        String mimeType() {
            return mimeType;
        }

        String extension() {
            return extension;
        }
    }
}
