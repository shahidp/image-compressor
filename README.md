# Image Compressor

A Spring Boot application for compressing images while maintaining quality and transparency.

## Project Overview

This is a web-based image compression tool built with Spring Boot that allows users to upload images and compress them to reduce file size while maintaining visual quality.

## Features

- **Transparent PNG Support**: Maintains transparency in PNG images during compression.
- **Quality Control**: Adjustable compression quality (0.0 to 1.0).
- **Size Reduction**: Significantly reduces image sizes from MB to KB range.
- **Web Interface**: User-friendly web UI for uploading and downloading compressed images.
- **REST API**: Backend API for programmatic image compression.
- **Two Compression Algorithms**:
  - **V1 (Stable)**: A balanced algorithm focusing on quality and compatibility.
  - **V2 (High Compression & Quality)**: An aggressive algorithm for maximum file size reduction with enhanced quality via Median Cut.

## Technology Stack

- **Framework**: Spring Boot 4.0.3
- **Template Engine**: Thymeleaf
- **Build Tool**: Maven
- **Java Version**: 17

## Project Structure

```
src/
├── main/
│   ├── java/com/itz/image_compressor/
│   │   ├── ImageCompressorApplication.java
│   │   ├── controller/
│   │   │   ├── ImageApiController.java
│   │   │   └── WebController.java
│   │   └── service/
│   │       ├── ImageCompressionService.java      # V1 (Stable)
│   │       └── ImageCompressionServiceV2.java    # V2 (High Compression & Quality)
```

## Image Compression Services

### V1: ImageCompressionService (Stable)

This is the default, stable compression service. It uses a 2048px max resolution and full-strength dithering to provide a good balance between quality and file size.

- **Max Resolution**: 2048px
- **Dithering**: Full strength (Floyd-Steinberg)
- **Default Quality**: 0.4
- **Use Case**: General-purpose compression where quality is a high priority.

### V2: ImageCompressionServiceV2 (High Compression & Quality)

This service is optimized for maximum file size reduction while significantly improving perceived quality through advanced color quantization. It uses a smaller resolution and Median Cut algorithm for palette generation.

- **Max Resolution**: 1600px
- **Dithering**: Reduced strength (85-90%)
- **Palette Generation**: Median Cut algorithm for superior color selection.
- **Default Quality**: 0.5
- **Use Case**: When the smallest possible file size is the primary goal, but with a strong emphasis on visual fidelity.

Both the `WebController` and `ImageApiController` are currently configured to use the **V2 service** by default.

## Running the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`

## API Endpoints

### Web UI
- `GET /` - Home page
- `GET /compress` - Compression page

### REST API
- `POST /api/compress` - Compress image
  - Request: Multipart file with optional `quality` parameter (0.0-1.0)
  - Response: Compressed image file
