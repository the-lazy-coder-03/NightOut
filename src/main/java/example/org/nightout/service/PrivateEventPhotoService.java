package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.dto.PrivateEventView;
import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventPhoto;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.repository.PrivateEventPhotoRepository;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.storage.StorageFile;
import example.org.nightout.storage.StorageResource;
import example.org.nightout.storage.StorageService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class PrivateEventPhotoService {

    private final PrivateEventPhotoRepository photoRepository;
    private final PrivateEventService privateEventService;
    private final UserManagementService userManagementService;
    private final StorageService storageService;
    private final ImageUploadValidator imageUploadValidator;
    private final AppProperties properties;
    private final Clock clock;

    public PrivateEventPhotoService(PrivateEventPhotoRepository photoRepository, PrivateEventService privateEventService,
                                    UserManagementService userManagementService, StorageService storageService,
                                    ImageUploadValidator imageUploadValidator, AppProperties properties, Clock clock) {
        this.photoRepository = photoRepository;
        this.privateEventService = privateEventService;
        this.userManagementService = userManagementService;
        this.storageService = storageService;
        this.imageUploadValidator = imageUploadValidator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PrivateEventPhoto> photosFor(AuthenticatedUser principal, String joinCode) {
        PrivateEvent event = privateEventService.requireAccessible(principal, joinCode).event();
        return photoRepository.findByPrivateEventOrderByUploadedAtDesc(event);
    }

    @Transactional
    public List<PrivateEventPhoto> uploadPhotos(AuthenticatedUser principal, String joinCode, MultipartFile[] files) {
        PrivateEventView view = privateEventService.requireAccessible(principal, joinCode);
        AppUser user = userManagementService.requireUser(principal.getId());
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
                .map(file -> uploadOne(view.event(), user, file))
                .toList();
    }

    @Transactional(readOnly = true)
    public PrivateEventPhoto privatePhoto(AuthenticatedUser principal, Long photoId) {
        PrivateEventPhoto photo = photoRepository.findWithPrivateEventById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Private event photo not found."));
        privateEventService.requireAccessible(principal, photo.getPrivateEvent().getJoinCode());
        return photo;
    }

    public StorageResource retrieve(PrivateEventPhoto photo) {
        return storageService.retrieve(photo.getStorageFileId());
    }

    private PrivateEventPhoto uploadOne(PrivateEvent event, AppUser user, MultipartFile multipartFile) {
        ValidatedImage image = imageUploadValidator.validate(multipartFile, properties.getMaxUploadBytes());
        String objectFilename = UUID.randomUUID() + "." + image.extension();
        StorageFile stored = storageService.upload(image.content(), objectFilename, image.mimeType(), storagePrefix(event));
        Instant now = Instant.now(clock);

        PrivateEventPhoto photo = new PrivateEventPhoto();
        photo.setPrivateEvent(event);
        photo.setUploadedBy(user);
        photo.setOriginalFilename(image.originalFilename());
        photo.setSafeFilename(event.getJoinCode() + "-" + objectFilename);
        photo.setMimeType(stored.mimeType());
        photo.setFileSize(stored.sizeBytes());
        photo.setStorageFileId(stored.id());
        photo.setUploadedAt(now);
        photo.setCreatedAt(now);
        return photoRepository.save(photo);
    }

    private static String storagePrefix(PrivateEvent event) {
        return "private-events/" + event.getJoinCode();
    }
}
