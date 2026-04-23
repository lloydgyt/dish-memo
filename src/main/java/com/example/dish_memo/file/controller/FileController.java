package com.example.dish_memo.file.controller;

import com.example.dish_memo.common.ApiResponse;
import com.example.dish_memo.file.dto.ImageUploadResponse;
import com.example.dish_memo.file.service.FileStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP API controller for image upload endpoints.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileStorageService fileStorageService;

    /**
     * Creates the controller with its storage dependency.
     *
     * @param fileStorageService image storage service
     */
    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Uploads one dish image.
     *
     * @param userId current user ID from gateway header
     * @param file multipart image file
     * @param bizType optional business type, accepted for API compatibility
     * @return uploaded image metadata
     */
    @PostMapping("/images")
    public ApiResponse<ImageUploadResponse> uploadImage(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "biz_type", required = false) String bizType
    ) {
        return ApiResponse.ok(fileStorageService.uploadImage(userId, file));
    }
}
