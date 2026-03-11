package com.itz.image_compressor.controller;

import com.itz.image_compressor.service.ImageCompressionServiceV2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/images")
@CrossOrigin(origins = "*")
public class ImageApiController {

    private final ImageCompressionServiceV2 imageCompressionService;

    public ImageApiController(@Qualifier("imageCompressionServiceV2") ImageCompressionServiceV2 imageCompressionService) {
        this.imageCompressionService = imageCompressionService;
    }

    /**
     * Upload and compress an image from a multipart form.
     * Best for browser-based uploads.
     */
    @PostMapping("/compress")
    public ResponseEntity<byte[]> compressImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "quality", defaultValue = "0.5") Float quality) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        if (quality < 0.0f || quality > 1.0f) {
            quality = 0.5f;
        }

        try {
            byte[] compressedImage = imageCompressionService.compressImage(file, quality);
            String newFilename = generateCompressedFilename(file.getOriginalFilename());
            
            return createSuccessResponse(compressedImage, newFilename);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Upload and compress an image from raw bytes in the request body.
     * Best for server-to-server integration.
     */
    @PostMapping("/compress-by-bytes")
    public ResponseEntity<byte[]> compressImageByBytes(
            @RequestBody byte[] imageBytes,
            @RequestHeader("Content-Type") String contentType,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "quality", defaultValue = "0.5") Float quality) {

        if (imageBytes == null || imageBytes.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        if (!contentType.startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        // Adapt the byte array to the MultipartFile interface our service expects
        MultipartFile multipartFile = new ByteArrayMultipartFile(
                imageBytes,
                "file",
                filename != null ? filename : "image.bin",
                contentType
        );

        try {
            byte[] compressedImage = imageCompressionService.compressImage(multipartFile, quality);
            String newFilename = generateCompressedFilename(filename);

            return createSuccessResponse(compressedImage, newFilename);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/info")
    public ResponseEntity<Map<String, Object>> getImageInfo(@RequestParam("file") MultipartFile file) {
        try {
            ImageCompressionServiceV2.ImageInfo info = imageCompressionService.getImageInfo(file);
            
            Map<String, Object> response = new HashMap<>();
            response.put("filename", info.filename());
            response.put("originalSize", info.originalSize());
            response.put("originalSizeFormatted", formatFileSize(info.originalSize()));
            response.put("width", info.width());
            response.put("height", info.height());
            response.put("contentType", info.contentType());
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Image Compressor API");
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<byte[]> createSuccessResponse(byte[] imageBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(imageBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(imageBytes);
    }

    private String generateCompressedFilename(String originalFilename) {
        String baseName = "compressed";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            baseName = (dotIndex > 0) ? originalFilename.substring(0, dotIndex) : originalFilename;
        }
        return baseName + "_compressed.png";
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    /**
     * A simple, self-contained implementation of MultipartFile for wrapping a byte array.
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String originalFilename;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String name, String originalFilename, String contentType) {
            this.content = content;
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}
