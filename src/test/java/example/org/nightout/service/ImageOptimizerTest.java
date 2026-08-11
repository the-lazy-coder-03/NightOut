package example.org.nightout.service;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class ImageOptimizerTest {

    private final ImageOptimizer optimizer = new ImageOptimizer();

    @Test
    void landscapeImageBecomesMaxWidth1080() throws Exception {
        OptimizedImage optimized = optimizer.optimize(jpeg(2000, 1000), 1080, 0.82);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(optimized.content()));

        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(540);
        assertThat(optimized.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void portraitImageBecomesMaxHeight1080() throws Exception {
        OptimizedImage optimized = optimizer.optimize(jpeg(1000, 2000), 1080, 0.82);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(optimized.content()));

        assertThat(image.getWidth()).isEqualTo(540);
        assertThat(image.getHeight()).isEqualTo(1080);
    }

    @Test
    void smallerImageIsNotUpscaled() throws Exception {
        OptimizedImage optimized = optimizer.optimize(jpeg(800, 600), 1080, 0.82);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(optimized.content()));

        assertThat(image.getWidth()).isEqualTo(800);
        assertThat(image.getHeight()).isEqualTo(600);
    }

    @Test
    void aspectRatioIsPreservedWithoutCropping() throws Exception {
        OptimizedImage optimized = optimizer.optimize(jpeg(1600, 900), 1080, 0.82);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(optimized.content()));

        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(608);
        assertThat(image.getWidth() / (double) image.getHeight()).isCloseTo(1600 / 900.0, org.assertj.core.data.Offset.offset(0.01));
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(24, 120, 104));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
