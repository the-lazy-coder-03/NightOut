package example.org.nightout.service;

import example.org.nightout.exception.StorageException;

import net.coobird.thumbnailator.Thumbnails;

import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

@Service
public class ImageOptimizer {

    public OptimizedImage optimize(byte[] originalContent, int maxDimension, double jpegQuality) {
        if (maxDimension < 1) {
            throw new StorageException("Image optimization max dimension must be at least 1.");
        }

        BufferedImage source = readImage(originalContent);
        Dimensions target = targetDimensions(source.getWidth(), source.getHeight(), maxDimension);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            Thumbnails.of(new ByteArrayInputStream(originalContent))
                    .size(target.width(), target.height())
                    .imageType(BufferedImage.TYPE_INT_RGB)
                    .outputFormat("jpg")
                    .outputQuality(clampQuality(jpegQuality))
                    .useExifOrientation(true)
                    .toOutputStream(output);
        } catch (IOException | IllegalArgumentException ex) {
            throw new StorageException("Could not optimize image.", ex);
        }
        return new OptimizedImage(output.toByteArray(), "image/jpeg", "jpg", target.width(), target.height());
    }

    private static BufferedImage readImage(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new StorageException("Could not read image for optimization.");
            }
            return image;
        } catch (IOException ex) {
            throw new StorageException("Could not read image for optimization.", ex);
        }
    }

    private static Dimensions targetDimensions(int width, int height, int maxDimension) {
        int longestSide = Math.max(width, height);
        if (longestSide <= maxDimension) {
            return new Dimensions(width, height);
        }

        double scale = maxDimension / (double) longestSide;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        return new Dimensions(targetWidth, targetHeight);
    }

    private static double clampQuality(double quality) {
        return Math.max(0.1, Math.min(1.0, quality));
    }

    private record Dimensions(int width, int height) {
    }
}
