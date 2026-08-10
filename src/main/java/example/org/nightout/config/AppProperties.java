package example.org.nightout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nightout")
public class AppProperties {

    private String baseUrl = "http://localhost:8080";
    private int retentionDays = 7;
    private long maxUploadBytes = 10 * 1024 * 1024;
    private int maxUploadCount = 12;
    private String storageProvider = "local";
    private String localStoragePath = "./storage/nightout";
    private boolean seedDemo = true;
    private NineDrive nineDrive = new NineDrive();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public int getMaxUploadCount() {
        return maxUploadCount;
    }

    public void setMaxUploadCount(int maxUploadCount) {
        this.maxUploadCount = maxUploadCount;
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

    public NineDrive getNineDrive() {
        return nineDrive;
    }

    public void setNineDrive(NineDrive nineDrive) {
        this.nineDrive = nineDrive;
    }

    public static class NineDrive {
        private String baseUrl = "http://localhost:4000";
        private String apiKey = "";
        private String email = "";
        private String password = "";
        private String folderId = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFolderId() {
            return folderId;
        }

        public void setFolderId(String folderId) {
            this.folderId = folderId;
        }
    }
}
