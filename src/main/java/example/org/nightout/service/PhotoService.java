package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.dto.PhotoPreloadView;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.entity.PhotoOptimizationStatus;
import example.org.nightout.entity.PhotoStatus;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.model.Area;
import example.org.nightout.repository.PhotoRepository;
import example.org.nightout.storage.StorageFile;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    private static final int AREA_PRELOAD_LIMIT_PER_CLUB = 12;

    private final PhotoRepository photoRepository;
    private final EventService eventService;
    private final EventPolicyService policyService;
    private final StorageService storageService;
    private final AppProperties properties;
    private final ImageOptimizationService imageOptimizationService;
    private final ImageUploadValidator imageUploadValidator;

    public PhotoService(PhotoRepository photoRepository, EventService eventService, EventPolicyService policyService, StorageService storageService, AppProperties properties, ImageOptimizationService imageOptimizationService, ImageUploadValidator imageUploadValidator) {
        this.photoRepository = photoRepository;
        this.eventService = eventService;
        this.policyService = policyService;
        this.storageService = storageService;
        this.properties = properties;
        this.imageOptimizationService = imageOptimizationService;
        this.imageUploadValidator = imageUploadValidator;
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
    public List<PhotoPreloadView> latestAreaPhotoPreloads(Area area) {
        LocalDate startDate = policyService.expiredCutoffDate();
        LocalDate endDate = policyService.currentGalleryDate();
        List<Photo> areaPhotos = photoRepository.findAreaApprovedPhotosForPreload(
                PhotoStatus.APPROVED,
                area.getDisplayName(),
                startDate,
                endDate
        );

        List<PhotoPreloadView> preloads = new ArrayList<>();
        Map<Long, LocalDate> preloadDateByClub = new HashMap<>();
        Map<Long, Integer> preloadCountByClub = new HashMap<>();

        for (Photo photo : areaPhotos) {
            NightEvent event = photo.getEvent();
            Long clubId = event.getClub().getId();
            LocalDate eventDate = event.getEventDate();
            LocalDate preloadDate = preloadDateByClub.computeIfAbsent(clubId, ignored -> eventDate);
            if (!preloadDate.equals(eventDate)) {
                continue;
            }

            int preloadCount = preloadCountByClub.getOrDefault(clubId, 0);
            if (preloadCount >= AREA_PRELOAD_LIMIT_PER_CLUB) {
                continue;
            }

            preloads.add(new PhotoPreloadView("/photos/" + photo.getId()));
            preloadCountByClub.put(clubId, preloadCount + 1);
        }
        return preloads;
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
        ValidatedImage image = imageUploadValidator.validate(multipartFile, properties.getMaxUploadBytes());
        String objectFilename = UUID.randomUUID() + "." + image.extension();
        String safeFilename = event.getClub().getSlug() + "-" + event.getEventDate() + "-" + objectFilename;
        StorageFile stored = storageService.upload(image.content(), objectFilename, image.mimeType(), storagePrefix(event));

        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setOriginalFilename(image.originalFilename());
        photo.setSafeFilename(safeFilename);
        photo.setMimeType(stored.mimeType());
        photo.setFileSize(stored.sizeBytes());
        photo.setStorageFileId(stored.id());
        photo.setStatus(PhotoStatus.APPROVED);
        photo.setOptimizationStatus(properties.getImageOptimization().isEnabled()
                ? PhotoOptimizationStatus.PENDING
                : PhotoOptimizationStatus.COMPLETE);
        photo.setUploadedAt(Instant.now());
        Photo saved = photoRepository.save(photo);
        enqueueOptimizationAfterCommit(saved);
        return saved;
    }

    private void enqueueOptimizationAfterCommit(Photo photo) {
        if (photo.getOptimizationStatus() != PhotoOptimizationStatus.PENDING) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueueOptimization(photo.getId());
                }
            });
            return;
        }
        enqueueOptimization(photo.getId());
    }

    private void enqueueOptimization(Long photoId) {
        try {
            imageOptimizationService.enqueue(photoId);
        } catch (RuntimeException ex) {
            log.warn("Failed to queue image optimization for photo {}; scheduled processing will retry it.", photoId, ex);
        }
    }

    private static String storagePrefix(NightEvent event) {
        String clubPrefix = event.getClub().getStorageFolderId();
        if (!StringUtils.hasText(clubPrefix)) {
            clubPrefix = "clubs/" + event.getClub().getSlug();
        }
        return clubPrefix + "/" + event.getEventDate();
    }
}
