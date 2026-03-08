package com.itz.image_compressor.controller;

import com.itz.image_compressor.service.ImageCompressionServiceV2;
import com.itz.image_compressor.service.ImageCompressionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
public class WebController {

    private final ImageCompressionServiceV2 imageCompressionService;

    public WebController(@Qualifier("imageCompressionServiceV2") ImageCompressionServiceV2 imageCompressionService) {
        this.imageCompressionService = imageCompressionService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/compress")
    public String compressPage() {
        return "compress";
    }

    @PostMapping("/compress")
    public String compressImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "quality", defaultValue = "0.5") Float quality,
            Model model) {

        if (file.isEmpty()) {
            model.addAttribute("error", "Please select an image file to upload");
            return "compress";
        }

        try {
            // Get original image info using a temporary instance of the old service
            // This is just for displaying original info, not for compression logic
            ImageCompressionService.ImageInfo originalInfo = new ImageCompressionService().getImageInfo(file);
            
            // Compress the image using the V2 service
            byte[] compressedImage = imageCompressionService.compressImage(file, quality);
            
            // Calculate compression stats
            long originalSize = originalInfo.originalSize();
            long compressedSize = compressedImage.length;
            double compressionRatio = ((double)(originalSize - compressedSize) / originalSize) * 100;
            
            // Encode compressed image to base64 for display
            String base64Image = java.util.Base64.getEncoder().encodeToString(compressedImage);
            
            // Add attributes to model
            model.addAttribute("originalFilename", originalInfo.filename());
            model.addAttribute("originalSize", formatFileSize(originalSize));
            model.addAttribute("compressedSize", formatFileSize(compressedSize));
            model.addAttribute("compressionRatio", String.format("%.1f", compressionRatio));
            model.addAttribute("width", originalInfo.width());
            model.addAttribute("height", originalInfo.height());
            model.addAttribute("quality", quality);
            model.addAttribute("compressedImage", base64Image);
            model.addAttribute("showResult", true);
            
        } catch (IOException e) {
            model.addAttribute("error", "Failed to compress image: " + e.getMessage());
        }

        return "compress";
    }

    /**
     * Download endpoint for the compressed image
     */
    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadCompressed(
            @RequestParam("imageData") String base64Data,
            @RequestParam("filename") String originalFilename) {
        
        try {
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
            String baseName = originalFilename.lastIndexOf('.') > 0 ? 
                originalFilename.substring(0, originalFilename.lastIndexOf('.')) : originalFilename;
            String newFilename = baseName + "_compressed.png";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("attachment", newFilename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
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
}
