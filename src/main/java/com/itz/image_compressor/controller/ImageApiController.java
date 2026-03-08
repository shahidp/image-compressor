package com.itz.image_compressor.controller;

import com.itz.image_compressor.service.ImageCompressionServiceV2;
import com.itz.image_compressor.service.ImageCompressionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
     * Upload and compress an image
     * 
     * @param file The image file to compress
     * @param quality Compression quality (0.0 to 1.0), optional, defaults to 0.5
     * @return Compressed PNG image
     */
    @PostMapping("/compress")
    public ResponseEntity<byte[]> compressImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "quality", defaultValue = "0.5") Float quality) {
        
        // Validate input
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate quality range
        if (quality < 0.0f || quality > 1.0f) {
            quality = 0.5f;
        }

        try {
            // Get original filename
            String originalFilename = file.getOriginalFilename();
            String baseName = originalFilename != null ? 
                originalFilename.substring(0, originalFilename.lastIndexOf('.')) : "image";

            // Compress the image
            byte[] compressedImage = imageCompressionService.compressImage(file, quality);

            // Generate new filename
            String newFilename = baseName + "_compressed.png";

            // Set headers for file download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("attachment", newFilename);
            headers.setContentLength(compressedImage.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(compressedImage);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get information about an uploaded image
     */
    @PostMapping("/info")
    public ResponseEntity<Map<String, Object>> getImageInfo(@RequestParam("file") MultipartFile file) {
        try {
            // We need to get info, we can use the V2 service's info method
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

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Image Compressor API");
        return ResponseEntity.ok(response);
    }

    /**
     * Format file size to human readable format
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}
