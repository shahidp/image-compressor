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

    // Use ImageCompressionServiceV2 for testing the latest logic
    private final ImageCompressionServiceV2 service = new ImageCompressionServiceV2();

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

        assertTrue(paletteSize > 16 && paletteSize <= 256, "Palette size should be in the 8-bit range (17-256 colors)");

        // Compare with the user-provided "img.png"
        File userImageFile = ResourceUtils.getFile("classpath:img.png");
        long userImageSize = userImageFile.length();
        System.out.println("User-provided img.png size: " + userImageSize + " bytes");

        double sizeDifference = Math.abs(generatedSize - userImageSize) / (double) userImageSize;
        System.out.println(String.format("Size difference is %.2f%%", sizeDifference * 100));
        assertTrue(sizeDifference < 0.15, "Generated size should be close to the user-provided image size");
    }

    @Test
    void compressImage_jpegExample_shouldBeTransparentPng() throws IOException {
        // Arrange
        File jpegExampleFile = ResourceUtils.getFile("classpath:jpeg-example.jpeg");
        byte[] originalContent = Files.readAllBytes(jpegExampleFile.toPath());
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "jpeg-example.jpeg",
                "image/jpeg",
                originalContent
        );

        long originalSize = originalContent.length;
        System.out.println("JPEG Example Original size: " + originalSize + " bytes");

        // Act
        byte[] compressedBytes = service.compressImage(multipartFile, 0.5f); // Using default quality
        long compressedSize = compressedBytes.length;
        System.out.println("JPEG Example Compressed size: " + compressedSize + " bytes");

        // Assert
        assertNotNull(compressedBytes);
        
        // 1. Output must be a valid PNG
        ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
        BufferedImage compressedImage = ImageIO.read(bis);
        assertNotNull(compressedImage, "Compressed bytes should form a valid image");
        assertEquals("png", ImageIO.getImageReaders(bis).next().getFormatName().toLowerCase(), "Output format should be PNG");

        // 2. Output must be transparent (TYPE_INT_ARGB or IndexColorModel with alpha)
        ColorModel cm = compressedImage.getColorModel();
        assertTrue(cm.hasAlpha(), "Output image should have an alpha channel (transparency)");
        
        // The assertion for size <= originalSize is removed here due to the conflict
        // with the "always transparent PNG" requirement for JPEG inputs.
        // The system will now return the smallest possible transparent PNG,
        // even if it's larger than the original JPEG.
    }
}
