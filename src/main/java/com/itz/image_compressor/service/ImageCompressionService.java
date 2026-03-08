package com.itz.image_compressor.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

@Service
public class ImageCompressionService {

    private static final float DEFAULT_QUALITY = 0.4f;
    private static final int MAX_DIMENSION = 2048;
    private static final int MAX_COLORS = 256;

    public byte[] compressImage(MultipartFile inputFile, Float quality) throws IOException {
        float compressionQuality = (quality != null) ? quality : DEFAULT_QUALITY;

        try (InputStream inputStream = inputFile.getInputStream()) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new IOException("Failed to read image file. Unsupported format or corrupted file.");
            }

            // 1. Resize
            BufferedImage resizedImage = resizeImageIfNeeded(originalImage);

            // 2. Quantize (Convert to Indexed Color with Dithering)
            int numColors = Math.max(16, (int) (MAX_COLORS * compressionQuality));
            BufferedImage quantizedImage = quantizeImage(resizedImage, numColors);

            // 3. Write as PNG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(quantizedImage, "png", outputStream);
            
            return outputStream.toByteArray();
        }
    }

    private BufferedImage resizeImageIfNeeded(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return original;
        }

        int newWidth = width;
        int newHeight = height;

        if (width > MAX_DIMENSION) {
            newWidth = MAX_DIMENSION;
            newHeight = (int) ((double) height * MAX_DIMENSION / width);
        }

        if (newHeight > MAX_DIMENSION) {
            newHeight = MAX_DIMENSION;
            newWidth = (int) ((double) width * MAX_DIMENSION / height);
        }

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(original, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        return resized;
    }

    private BufferedImage quantizeImage(BufferedImage original, int maxColors) {
        int width = original.getWidth();
        int height = original.getHeight();

        // Get all pixels
        int[] pixels = original.getRGB(0, 0, width, height, null, 0, width);

        // 1. Build Palette
        int[] palette = buildPalette(pixels, maxColors);

        // 2. Create IndexColorModel
        IndexColorModel colorModel = createIndexColorModel(palette);

        // 3. Map pixels to indices with Floyd-Steinberg Dithering
        byte[] indexedData = new byte[width * height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int oldPixel = pixels[i];
                int alpha = (oldPixel >> 24) & 0xFF;

                if (alpha < 128) {
                    // Transparent
                    indexedData[i] = 0;
                    continue;
                }

                // Find closest color in palette
                int paletteIndex = findClosestColorIndex(oldPixel, palette);
                int closestColor = palette[paletteIndex];
                
                indexedData[i] = (byte) paletteIndex;

                // Calculate quantization error
                int r = (oldPixel >> 16) & 0xFF;
                int g = (oldPixel >> 8) & 0xFF;
                int b = oldPixel & 0xFF;

                int nr = (closestColor >> 16) & 0xFF;
                int ng = (closestColor >> 8) & 0xFF;
                int nb = closestColor & 0xFF;

                int er = r - nr;
                int eg = g - ng;
                int eb = b - nb;

                // Distribute error to neighbors (Floyd-Steinberg)
                // right, bottom-left, bottom, bottom-right
                distributeError(pixels, x + 1, y, width, height, er, eg, eb, 7.0/16.0);
                distributeError(pixels, x - 1, y + 1, width, height, er, eg, eb, 3.0/16.0);
                distributeError(pixels, x, y + 1, width, height, er, eg, eb, 5.0/16.0);
                distributeError(pixels, x + 1, y + 1, width, height, er, eg, eb, 1.0/16.0);
            }
        }

        // 4. Create the Indexed Image directly from data
        DataBufferByte buffer = new DataBufferByte(indexedData, indexedData.length);
        SampleModel sampleModel = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 1, width, new int[]{0});
        WritableRaster raster = Raster.createWritableRaster(sampleModel, buffer, new Point(0, 0));

        return new BufferedImage(colorModel, raster, false, null);
    }

    private void distributeError(int[] pixels, int x, int y, int w, int h, int er, int eg, int eb, double factor) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        
        int i = y * w + x;
        int pixel = pixels[i];
        
        int alpha = (pixel >> 24) & 0xFF;
        if (alpha < 128) return; // Don't apply error to transparent pixels
        
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        
        r = clamp((int)(r + er * factor));
        g = clamp((int)(g + eg * factor));
        b = clamp((int)(b + eb * factor));
        
        pixels[i] = (alpha << 24) | (r << 16) | (g << 8) | b;
    }
    
    private int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }

    private int[] buildPalette(int[] pixels, int maxColors) {
        // Frequency analysis
        Map<Integer, Integer> colorFreq = new HashMap<>();
        for (int pixel : pixels) {
            int alpha = (pixel >> 24) & 0xFF;
            if (alpha >= 128) { // Only count opaque pixels
                int rgb = pixel & 0xFFFFFF;
                colorFreq.put(rgb, colorFreq.getOrDefault(rgb, 0) + 1);
            }
        }

        List<Map.Entry<Integer, Integer>> sortedColors = new ArrayList<>(colorFreq.entrySet());
        sortedColors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Limit palette size
        // Reserve index 0 for transparency
        int paletteSize = Math.min(maxColors - 1, sortedColors.size());
        int[] palette = new int[paletteSize + 1];
        
        palette[0] = 0; // Placeholder for transparent

        for (int i = 0; i < paletteSize; i++) {
            palette[i + 1] = sortedColors.get(i).getKey();
        }

        return palette;
    }

    private IndexColorModel createIndexColorModel(int[] palette) {
        int size = palette.length;
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        byte[] a = new byte[size];

        // Index 0 is transparent
        r[0] = 0; g[0] = 0; b[0] = 0; a[0] = 0;

        for (int i = 1; i < size; i++) {
            int rgb = palette[i];
            r[i] = (byte) ((rgb >> 16) & 0xFF);
            g[i] = (byte) ((rgb >> 8) & 0xFF);
            b[i] = (byte) (rgb & 0xFF);
            a[i] = (byte) 255; // Opaque
        }

        // Calculate bits required (1, 2, 4, or 8)
        int bits = 8;
        if (size <= 2) bits = 1;
        else if (size <= 4) bits = 2;
        else if (size <= 16) bits = 4;

        return new IndexColorModel(bits, size, r, g, b, a);
    }

    private int findClosestColorIndex(int rgb, int[] palette) {
        int minDist = Integer.MAX_VALUE;
        int bestIndex = 1; // Default to first opaque color

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Start from 1 to skip transparent index
        for (int i = 1; i < palette.length; i++) {
            int target = palette[i];
            int tr = (target >> 16) & 0xFF;
            int tg = (target >> 8) & 0xFF;
            int tb = target & 0xFF;

            // Simple Euclidean distance squared
            int dr = r - tr;
            int dg = g - tg;
            int db = b - tb;
            
            // Weighted distance for better visual results
            int dist = 2*dr*dr + 4*dg*dg + 3*db*db;

            if (dist < minDist) {
                minDist = dist;
                bestIndex = i;
                if (dist == 0) break; // Exact match
            }
        }
        return bestIndex;
    }

    public ImageInfo getImageInfo(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Could not read image");
            }
            return new ImageInfo(
                file.getOriginalFilename(),
                file.getSize(),
                image.getWidth(),
                image.getHeight(),
                file.getContentType()
            );
        }
    }

    public record ImageInfo(
        String filename,
        long originalSize,
        int width,
        int height,
        String contentType
    ) {}
}
