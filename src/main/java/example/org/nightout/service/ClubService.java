package example.org.nightout.service;

import example.org.nightout.entity.Club;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.model.Area;
import example.org.nightout.repository.ClubRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final SlugService slugService;
    private final ClubImageService clubImageService;

    public ClubService(ClubRepository clubRepository, SlugService slugService, ClubImageService clubImageService) {
        this.clubRepository = clubRepository;
        this.slugService = slugService;
        this.clubImageService = clubImageService;
    }

    @Transactional(readOnly = true)
    public List<Club> activeClubs() {
        return clubRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Club> activeClubsForArea(Area area) {
        return clubRepository.findByActiveTrueAndAreaOrderByNameAsc(area.getDisplayName());
    }

    public List<Area> areas() {
        return Area.all();
    }

    public Area requireAreaBySlug(String slug) {
        return Area.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found."));
    }

    @Transactional(readOnly = true)
    public List<Club> allClubs() {
        return clubRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Club requireActiveBySlug(String slug) {
        return clubRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found."));
    }

    @Transactional(readOnly = true)
    public Club requireById(Long id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found."));
    }

    @Transactional
    public Club create(String name, String slug, String city, String area, String address, MultipartFile clubImage, String storageFolderId, boolean active) {
        Club club = new Club();
        apply(club, name, slug, city, area, address, storageFolderId, active);
        clubImageService.upload(clubImage, club.getSlug()).ifPresent(upload -> applyImage(club, upload));
        return clubRepository.save(club);
    }

    @Transactional
    public Club update(Long id, String name, String slug, String city, String area, String address, MultipartFile clubImage, String storageFolderId, boolean active) {
        Club club = requireById(id);
        String oldImageStorageFileId = club.getImageStorageFileId();
        apply(club, name, slug, city, area, address, storageFolderId, active);
        Optional<ClubImageUpload> replacement = clubImageService.upload(clubImage, club.getSlug());
        replacement.ifPresent(upload -> applyImage(club, upload));
        Club saved = clubRepository.save(club);
        replacement
                .filter(upload -> !upload.storageFileId().equals(oldImageStorageFileId))
                .ifPresent(upload -> deleteAfterCommit(oldImageStorageFileId));
        return saved;
    }

    @Transactional
    public void disable(Long id) {
        Club club = requireById(id);
        club.setActive(false);
    }

    private void apply(Club club, String name, String slug, String city, String area, String address, String storageFolderId, boolean active) {
        club.setName(requireText(name, "Club name is required."));
        club.setSlug(StringUtils.hasText(slug) ? slugService.slugify(slug) : slugService.slugify(name));
        club.setCity(requireText(city, "City is required."));
        club.setArea(Area.requireDisplayName(area).getDisplayName());
        club.setAddress(blankToNull(address));
        club.setStorageFolderId(blankToNull(storageFolderId));
        club.setActive(active);
    }

    private static void applyImage(Club club, ClubImageUpload upload) {
        club.setImageStorageFileId(upload.storageFileId());
        club.setImageMimeType(upload.mimeType());
        club.setImageFileSize(upload.fileSize());
        club.setImageUploadedAt(upload.uploadedAt());
    }

    private void deleteAfterCommit(String storageFileId) {
        if (!StringUtils.hasText(storageFileId)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clubImageService.deleteQuietly(storageFileId);
                }
            });
            return;
        }
        clubImageService.deleteQuietly(storageFileId);
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
