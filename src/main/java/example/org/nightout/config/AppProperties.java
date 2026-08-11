package example.org.nightout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nightout")
public class AppProperties {

    private String baseUrl = "http://localhost:8090";
    private String timeZone = "Africa/Johannesburg";
    private int retentionDays = 7;
    private int privateEventRetentionDays = 32;
    private long maxUploadBytes = 25 * 1024 * 1024;
    private long maxRequestBytes = 300 * 1024 * 1024;
    private int maxUploadCount = 12;
    private ImageOptimization imageOptimization = new ImageOptimization();
    private String storageProvider = "local";
    private String localStoragePath = "./storage/nightout";
    private boolean seedDemo = true;
    private String seedAdminEmail = "admin@nightout.local";
    private String seedClubOwnerEmail = "owner@nightout.local";
    private String schemaVersion = "1";
    private boolean schemaResetAllowed;
    private S3 s3 = new S3();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getPrivateEventRetentionDays() {
        return privateEventRetentionDays;
    }

    public void setPrivateEventRetentionDays(int privateEventRetentionDays) {
        this.privateEventRetentionDays = privateEventRetentionDays;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public long getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public int getMaxUploadCount() {
        return maxUploadCount;
    }

    public void setMaxUploadCount(int maxUploadCount) {
        this.maxUploadCount = maxUploadCount;
    }

    public ImageOptimization getImageOptimization() {
        return imageOptimization;
    }

    public void setImageOptimization(ImageOptimization imageOptimization) {
        this.imageOptimization = imageOptimization;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getLocalStoragePath() {
        return localStoragePath;
    }

    public void setLocalStoragePath(String localStoragePath) {
        this.localStoragePath = localStoragePath;
    }

    public boolean isSeedDemo() {
        return seedDemo;
    }

    public void setSeedDemo(boolean seedDemo) {
        this.seedDemo = seedDemo;
    }

    public String getSeedAdminEmail() {
        return seedAdminEmail;
    }

    public void setSeedAdminEmail(String seedAdminEmail) {
        this.seedAdminEmail = seedAdminEmail;
    }

    public String getSeedClubOwnerEmail() {
        return seedClubOwnerEmail;
    }

    public void setSeedClubOwnerEmail(String seedClubOwnerEmail) {
        this.seedClubOwnerEmail = seedClubOwnerEmail;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public boolean isSchemaResetAllowed() {
        return schemaResetAllowed;
    }

    public void setSchemaResetAllowed(boolean schemaResetAllowed) {
        this.schemaResetAllowed = schemaResetAllowed;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    public static class S3 {
        private String endpoint = "http://127.0.0.1:8080";
        private String bucket = "nightout";
        private String region = "us-east-1";
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }
    }

    public static class ImageOptimization {
        private boolean enabled = true;
        private int maxDimension = 1080;
        private double jpegQuality = 0.82;
        private boolean deleteOriginal = true;
        private int batchSize = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxDimension() {
            return maxDimension;
        }

        public void setMaxDimension(int maxDimension) {
            this.maxDimension = maxDimension;
        }

        public double getJpegQuality() {
            return jpegQuality;
        }

        public void setJpegQuality(double jpegQuality) {
            this.jpegQuality = jpegQuality;
        }

        public boolean isDeleteOriginal() {
            return deleteOriginal;
        }

        public void setDeleteOriginal(boolean deleteOriginal) {
            this.deleteOriginal = deleteOriginal;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
