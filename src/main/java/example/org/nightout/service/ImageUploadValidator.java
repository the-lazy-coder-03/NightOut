package example.org.nightout.service;

import example.org.nightout.exception.BusinessRuleException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class ImageUploadValidator {

    public ValidatedImage validate(MultipartFile multipartFile, long maxUploadBytes) {
        if (multipartFile.getSize() > maxUploadBytes) {
            throw new BusinessRuleException("Each image must be smaller than " + Math.max(1, maxUploadBytes / 1024 / 1024) + " MB.");
        }
        byte[] bytes;
        try {
            bytes = multipartFile.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("Could not read the uploaded image.");
        }
        return validate(bytes, multipartFile.getOriginalFilename(), multipartFile.getContentType());
    }

    public ValidatedImage validate(byte[] bytes, String filename, String contentType) {
        ImageType imageType = detectImageType(bytes, filename, contentType);
        return new ValidatedImage(bytes, safeOriginalFilename(filename), imageType.mimeType(), imageType.extension());
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
