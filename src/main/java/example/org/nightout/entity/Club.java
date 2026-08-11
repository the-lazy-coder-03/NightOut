package example.org.nightout.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clubs")
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String area;

    private String address;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "storage_folder_id")
    private String storageFolderId;

    @Column(name = "image_storage_file_id")
    private String imageStorageFileId;

    @Column(name = "image_mime_type")
    private String imageMimeType;

    @Column(name = "image_file_size")
    private Long imageFileSize;

    @Column(name = "image_uploaded_at")
    private Instant imageUploadedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "club", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NightEvent> events = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getStorageFolderId() {
        return storageFolderId;
    }

    public void setStorageFolderId(String storageFolderId) {
        this.storageFolderId = storageFolderId;
    }

    public String getImageStorageFileId() {
        return imageStorageFileId;
    }

    public void setImageStorageFileId(String imageStorageFileId) {
        this.imageStorageFileId = imageStorageFileId;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }

    public Long getImageFileSize() {
        return imageFileSize;
    }

    public void setImageFileSize(Long imageFileSize) {
        this.imageFileSize = imageFileSize;
    }

    public Instant getImageUploadedAt() {
        return imageUploadedAt;
    }

    public void setImageUploadedAt(Instant imageUploadedAt) {
        this.imageUploadedAt = imageUploadedAt;
    }

    public boolean hasUploadedImage() {
        return imageStorageFileId != null && !imageStorageFileId.isBlank();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<NightEvent> getEvents() {
        return events;
    }
}
