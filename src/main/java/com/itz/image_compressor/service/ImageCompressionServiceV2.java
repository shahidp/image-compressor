package com.itz.image_compressor.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;

@Service("imageCompressionServiceV2")
public class ImageCompressionServiceV2 {

    private static final float DEFAULT_QUALITY = 0.5f;
    private static final int MAX_DIMENSION = 1600;
    private static final int MAX_COLORS = 256;
    private static final float DITHER_STRENGTH = 0.9f;

    public byte[] compressImage(MultipartFile inputFile, Float quality) throws IOException {
        float compressionQuality = (quality != null) ? quality : DEFAULT_QUALITY;

        byte[] originalFileBytes = inputFile.getBytes();
        String originalContentType = inputFile.getContentType();

        try (InputStream inputStream = new ByteArrayInputStream(originalFileBytes)) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                return originalFileBytes; // Cannot process, return original
            }

            // Always resize first
            BufferedImage resizedImage = resizeImageIfNeeded(originalImage);

            // Strategy 1: Attempt to create a highly compressed, quantized, indexed PNG
            byte[] indexedPngBytes = null;
            try {
                int numColors = Math.max(16, (int) (MAX_COLORS * compressionQuality));
                BufferedImage quantizedImage = quantizeImage(resizedImage, numColors);
                ByteArrayOutputStream quantizedOutputStream = new ByteArrayOutputStream();
                ImageIO.write(quantizedImage, "png", quantizedOutputStream);
                indexedPngBytes = quantizedOutputStream.toByteArray();
            } catch (Exception e) {
                // Ignore and fall back
            }

            // Strategy 2: Create a standard, full-color transparent PNG (simple conversion)
            // This is our fallback if the indexed version looks bad or failed, 
            // OR if we just want to compare sizes to find the absolute best PNG.
            byte[] standardPngBytes = null;
            try {
                ByteArrayOutputStream simplePngOutputStream = new ByteArrayOutputStream();
                ImageIO.write(resizedImage, "png", simplePngOutputStream);
                standardPngBytes = simplePngOutputStream.toByteArray();
            } catch (Exception e) {
                 // Ignore
            }
            
            // Decision Logic
            
            // 1. If original was PNG, we have a hard baseline: the original file.
            if (originalContentType != null && originalContentType.equals("image/png")) {
                long minSize = originalFileBytes.length;
                byte[] bestBytes = originalFileBytes;

                if (indexedPngBytes != null && indexedPngBytes.length < minSize) {
                    minSize = indexedPngBytes.length;
                    bestBytes = indexedPngBytes;
                }
                
                // We typically don't check standardPngBytes here because re-saving a PNG 
                // usually doesn't make it smaller unless we resized it. 
                // But since we DID resize (potentially), let's check it too.
                if (standardPngBytes != null && standardPngBytes.length < minSize) {
                    bestBytes = standardPngBytes;
                }
                
                return bestBytes;
            } 
            
            // 2. If original was NOT PNG (e.g. JPEG), we MUST return a PNG.
            // We cannot return the original. We must pick the smaller of our two PNG candidates.
            else {
                if (indexedPngBytes == null && standardPngBytes == null) {
                    // Both failed? Should be impossible, but return original as failsafe.
                    return originalFileBytes; 
                }
                
                if (indexedPngBytes != null && standardPngBytes != null) {
                    // Return the smaller of the two PNGs
                    return (indexedPngBytes.length < standardPngBytes.length) ? indexedPngBytes : standardPngBytes;
                }
                
                return (indexedPngBytes != null) ? indexedPngBytes : standardPngBytes;
            }
        }
    }

    private BufferedImage resizeImageIfNeeded(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return original;
        }

        int newWidth = width > height ? MAX_DIMENSION : (int) ((double) width * MAX_DIMENSION / height);
        int newHeight = height >= width ? MAX_DIMENSION : (int) ((double) height * MAX_DIMENSION / width);

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
        int[] pixels = original.getRGB(0, 0, width, height, null, 0, width);

        int[] palette = buildMedianCutPalette(pixels, maxColors);
        IndexColorModel colorModel = createIndexColorModel(palette);
        byte[] indexedData = new byte[width * height];
        
        int[] ditherPixels = Arrays.copyOf(pixels, pixels.length);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int oldPixel = ditherPixels[i];
                if (((oldPixel >> 24) & 0xFF) < 128) {
                    indexedData[i] = 0; // Transparent
                    continue;
                }

                int paletteIndex = findClosestColorIndex(oldPixel, palette);
                indexedData[i] = (byte) paletteIndex;
                int closestColor = palette[paletteIndex];

                int er = ((oldPixel >> 16) & 0xFF) - ((closestColor >> 16) & 0xFF);
                int eg = ((oldPixel >> 8) & 0xFF) - ((closestColor >> 8) & 0xFF);
                int eb = (oldPixel & 0xFF) - (closestColor & 0xFF);

                distributeError(ditherPixels, x + 1, y, width, height, er, eg, eb, 7.0 / 16.0);
                distributeError(ditherPixels, x - 1, y + 1, width, height, er, eg, eb, 3.0 / 16.0);
                distributeError(ditherPixels, x, y + 1, width, height, er, eg, eb, 5.0 / 16.0);
                distributeError(ditherPixels, x + 1, y + 1, width, height, er, eg, eb, 1.0 / 16.0);
            }
        }

        DataBufferByte buffer = new DataBufferByte(indexedData, indexedData.length);
        SampleModel sampleModel = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 1, width, new int[]{0});
        WritableRaster raster = Raster.createWritableRaster(sampleModel, buffer, new Point(0, 0));
        return new BufferedImage(colorModel, raster, false, null);
    }
    
    private IndexColorModel createIndexColorModel(int[] palette) {
        int size = palette.length;
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        byte[] a = new byte[size];

        r[0] = 0; g[0] = 0; b[0] = 0; a[0] = 0;

        for (int i = 1; i < size; i++) {
            int rgb = palette[i];
            r[i] = (byte) ((rgb >> 16) & 0xFF);
            g[i] = (byte) ((rgb >> 8) & 0xFF);
            b[i] = (byte) (rgb & 0xFF);
            a[i] = (byte) 255;
        }

        int bits = 8;
        if (size <= 2) bits = 1;
        else if (size <= 4) bits = 2;
        else if (size <= 16) bits = 4;

        return new IndexColorModel(bits, size, r, g, b, a);
    }

    private int[] buildMedianCutPalette(int[] pixels, int maxColors) {
        List<int[]> colorList = new ArrayList<>();
        for (int pixel : pixels) {
            if (((pixel >> 24) & 0xFF) >= 128) {
                colorList.add(new int[]{(pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF});
            }
        }

        if (colorList.size() <= maxColors) {
            Set<Integer> uniqueColors = new HashSet<>();
            for (int[] c : colorList) uniqueColors.add((c[0] << 16) | (c[1] << 8) | c[2]);
            int[] palette = new int[uniqueColors.size() + 1];
            palette[0] = 0;
            int i = 1;
            for (Integer color : uniqueColors) palette[i++] = color;
            return palette;
        }

        PriorityQueue<ColorBox> pq = new PriorityQueue<>(Collections.reverseOrder());
        pq.add(new ColorBox(colorList));

        while (pq.size() < maxColors) {
            ColorBox box = pq.poll();
            if (box == null || box.getPixelCount() < 2) break;
            ColorBox[] newBoxes = box.split();
            pq.add(newBoxes[0]);
            pq.add(newBoxes[1]);
        }

        int[] palette = new int[pq.size() + 1];
        palette[0] = 0; // Transparent
        int i = 1;
        for (ColorBox box : pq) {
            palette[i++] = box.getAverageColor();
        }
        return palette;
    }

    private static class ColorBox implements Comparable<ColorBox> {
        private final List<int[]> pixels;
        private final int minR, maxR, minG, maxG, minB, maxB;

        ColorBox(List<int[]> pixels) {
            this.pixels = pixels;
            int[] min = {255, 255, 255}, max = {0, 0, 0};
            for (int[] p : pixels) {
                for (int i = 0; i < 3; i++) {
                    min[i] = Math.min(min[i], p[i]);
                    max[i] = Math.max(max[i], p[i]);
                }
            }
            minR = min[0]; maxR = max[0]; minG = min[1]; maxG = max[1]; minB = min[2]; maxB = max[2];
        }

        public int getPixelCount() { return pixels.size(); }

        public ColorBox[] split() {
            int dr = maxR - minR, dg = maxG - minG, db = maxB - minB;
            int dim = (dr >= dg && dr >= db) ? 0 : (dg >= db ? 1 : 2);
            pixels.sort(Comparator.comparingInt(p -> p[dim]));
            int mid = pixels.size() / 2;
            return new ColorBox[]{new ColorBox(pixels.subList(0, mid)), new ColorBox(pixels.subList(mid, pixels.size()))};
        }

        public int getAverageColor() {
            long r = 0, g = 0, b = 0;
            for (int[] p : pixels) { r += p[0]; g += p[1]; b += p[2]; }
            r /= pixels.size(); g /= pixels.size(); b /= pixels.size();
            return ((int) r << 16) | ((int) g << 8) | (int) b;
        }

        @Override
        public int compareTo(ColorBox other) {
            return Integer.compare(this.getPixelCount(), other.getPixelCount());
        }
    }

    private void distributeError(int[] pixels, int x, int y, int w, int h, int er, int eg, int eb, double factor) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        int i = y * w + x;
        if (((pixels[i] >> 24) & 0xFF) < 128) return;
        int r = clamp(((pixels[i] >> 16) & 0xFF) + (int) (er * factor * DITHER_STRENGTH));
        int g = clamp(((pixels[i] >> 8) & 0xFF) + (int) (eg * factor * DITHER_STRENGTH));
        int b = clamp((pixels[i] & 0xFF) + (int) (eb * factor * DITHER_STRENGTH));
        pixels[i] = (pixels[i] & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
    
    private int clamp(int val) { return Math.max(0, Math.min(255, val)); }

    private int findClosestColorIndex(int rgb, int[] palette) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int minDist = Integer.MAX_VALUE, bestIndex = 1;
        for (int i = 1; i < palette.length; i++) {
            int tr = (palette[i] >> 16) & 0xFF, tg = (palette[i] >> 8) & 0xFF, tb = palette[i] & 0xFF;
            int dist = 2 * (r - tr) * (r - tr) + 4 * (g - tg) * (g - tg) + 3 * (b - tb) * (b - tb);
            if (dist < minDist) {
                minDist = dist;
                bestIndex = i;
                if (dist == 0) break;
            }
        }
        return bestIndex;
    }

    public ImageInfo getImageInfo(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) throw new IOException("Could not read image");
            return new ImageInfo(file.getOriginalFilename(), file.getSize(), image.getWidth(), image.getHeight(), file.getContentType());
        }
    }

    public record ImageInfo(String filename, long originalSize, int width, int height, String contentType) {}
}
