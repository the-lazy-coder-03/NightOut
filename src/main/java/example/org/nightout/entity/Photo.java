package example.org.nightout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "photos")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private NightEvent event;

    @Column(name = "storage_file_id", nullable = false)
    private String storageFileId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "safe_filename", nullable = false)
    private String safeFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "optimization_status", nullable = false)
    private PhotoOptimizationStatus optimizationStatus = PhotoOptimizationStatus.COMPLETE;

    @Column(name = "optimization_attempts", nullable = false)
    private int optimizationAttempts;

    @Column(name = "optimization_error")
    private String optimizationError;

    @Column(name = "optimization_started_at")
    private Instant optimizationStartedAt;

    @Column(name = "optimized_at")
    private Instant optimizedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStatus status = PhotoStatus.APPROVED;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public NightEvent getEvent() {
        return event;
    }

    public void setEvent(NightEvent event) {
        this.event = event;
    }

    public String getStorageFileId() {
        return storageFileId;
    }

    public void setStorageFileId(String storageFileId) {
        this.storageFileId = storageFileId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getSafeFilename() {
        return safeFilename;
    }

    public void setSafeFilename(String safeFilename) {
        this.safeFilename = safeFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public PhotoOptimizationStatus getOptimizationStatus() {
        return optimizationStatus;
    }

    public void setOptimizationStatus(PhotoOptimizationStatus optimizationStatus) {
        this.optimizationStatus = optimizationStatus;
    }

    public int getOptimizationAttempts() {
        return optimizationAttempts;
    }

    public void setOptimizationAttempts(int optimizationAttempts) {
        this.optimizationAttempts = optimizationAttempts;
    }

    public String getOptimizationError() {
        return optimizationError;
    }

    public void setOptimizationError(String optimizationError) {
        this.optimizationError = optimizationError;
    }

    public Instant getOptimizationStartedAt() {
        return optimizationStartedAt;
    }

    public void setOptimizationStartedAt(Instant optimizationStartedAt) {
        this.optimizationStartedAt = optimizationStartedAt;
    }

    public Instant getOptimizedAt() {
        return optimizedAt;
    }

    public void setOptimizedAt(Instant optimizedAt) {
        this.optimizedAt = optimizedAt;
    }

    public PhotoStatus getStatus() {
        return status;
    }

    public void setStatus(PhotoStatus status) {
        this.status = status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
