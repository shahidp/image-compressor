package com.itz.image_compressor.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.ResourceUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class ImageCompressionServiceTest {

    private final ImageCompressionService service = new ImageCompressionService();

    @Test
    void compressImage_ShouldReduceSizeSignificantly() throws IOException {
        // Arrange
        File file = ResourceUtils.getFile("classpath:test.jpg");
        byte[] content = Files.readAllBytes(file.toPath());
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                content
        );

        long originalSize = content.length;
        System.out.println("Original size: " + originalSize + " bytes");

        // Act
        byte[] compressedBytes = service.compressImage(multipartFile, 0.1f);
        long compressedSize = compressedBytes.length;
        System.out.println("Compressed size (quality 0.1): " + compressedSize + " bytes");

        // Assert
        assertNotNull(compressedBytes);
        assertTrue(compressedSize < originalSize, "Compressed size should be smaller than original size");
        
        ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
        BufferedImage compressedImage = ImageIO.read(bis);
        assertNotNull(compressedImage, "Compressed bytes should form a valid image");
        
        assertTrue(compressedSize < originalSize * 0.5, "Compression should reduce size by at least 50%");
    }

    @Test
    void compressImage_WithQuality_0_5_ShouldBalanceSizeAndQuality() throws IOException {
        // Arrange
        File testImageFile = ResourceUtils.getFile("classpath:test.jpg");
        byte[] content = Files.readAllBytes(testImageFile.toPath());
        MockMultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", content);

        // Act
        byte[] compressedBytes = service.compressImage(multipartFile, 0.5f);
        long generatedSize = compressedBytes.length;
        System.out.println("Generated size (quality 0.5): " + generatedSize + " bytes");

        // Assert
        assertNotNull(compressedBytes);
        assertTrue(generatedSize > 0);

        // Analyze the generated image
        ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
        BufferedImage compressedImage = ImageIO.read(bis);
        assertNotNull(compressedImage, "Generated image should be valid");

        ColorModel cm = compressedImage.getColorModel();
        assertTrue(cm instanceof IndexColorModel, "Generated image should have an indexed color model");
        IndexColorModel icm = (IndexColorModel) cm;
        int paletteSize = icm.getMapSize();
        System.out.println("Generated image palette size: " + paletteSize);

        // For quality 0.5, we expect an 8-bit palette.
        // The in-memory representation might be padded to 256, so we check the range.
        assertTrue(paletteSize > 16 && paletteSize <= 256, "Palette size should be in the 8-bit range (17-256 colors)");

        // Compare with the user-provided "img.png"
        File userImageFile = ResourceUtils.getFile("classpath:img.png");
        long userImageSize = userImageFile.length();
        System.out.println("User-provided img.png size: " + userImageSize + " bytes");

        // Check if the generated size is in the same ballpark as the user's image
        // Allowing a 15% tolerance for variations in dithering/palette generation
        double sizeDifference = Math.abs(generatedSize - userImageSize) / (double) userImageSize;
        System.out.println(String.format("Size difference is %.2f%%", sizeDifference * 100));
        assertTrue(sizeDifference < 0.15, "Generated size should be close to the user-provided image size");
    }
}
