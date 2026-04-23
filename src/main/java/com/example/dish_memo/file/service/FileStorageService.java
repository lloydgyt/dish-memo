package com.example.dish_memo.file.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.common.StructuredLogUtils;
import com.example.dish_memo.file.dto.ImageUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * MVP local image storage service for dish image uploads.
 */
@Service
public class FileStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path baseDir;
    private final String publicPrefix;

    /**
     * Creates the file storage service from environment-backed configuration.
     *
     * @param baseDir upload directory
     * @param publicPrefix URL prefix for uploaded files
     */
    public FileStorageService(
            @Value("${app.upload.base-dir}") String baseDir,
            @Value("${app.upload.public-prefix}") String publicPrefix
    ) {
        this.baseDir = Path.of(baseDir);
        this.publicPrefix = publicPrefix.endsWith("/") ? publicPrefix.substring(0, publicPrefix.length() - 1) : publicPrefix;
    }

    /**
     * Validates and stores an uploaded image, returning the public access metadata.
     *
     * @param userId current user ID
     * @param file multipart image file
     * @return uploaded image metadata
     */
    public ImageUploadResponse uploadImage(String userId, MultipartFile file) {
        LOGGER.info(StructuredLogUtils.info(userId, "upload dish image"));
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "image file is required");
        }
        String extension = validateImage(file);
        String fileId = "img_" + UUID.randomUUID().toString().replace("-", "");
        Path targetDir = baseDir.resolve("dish");
        Path target = targetDir.resolve(fileId + "." + extension);
        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
            ImageSize imageSize = readImageSize(file);
            String image_url = publicPrefix + "/dish/" + target.getFileName();
            LOGGER.info(StructuredLogUtils.info(userId, "image uploaded, image_url: " + image_url));
            return new ImageUploadResponse(fileId, image_url, imageSize.width(), imageSize.height());
        } catch (IOException ex) {
            LOGGER.warn(StructuredLogUtils.exception(userId, "upload dish image failed", ex));
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "file upload failed");
        }
    }

    private String validateImage(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(filename) && filename.lastIndexOf('.') >= 0) {
            extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "unsupported image type");
        }
        return extension.equals("jpeg") ? "jpg" : extension;
    }

    private ImageSize readImageSize(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                return new ImageSize(0, 0);
            }
            return new ImageSize(image.getWidth(), image.getHeight());
        }
    }

    private record ImageSize(int width, int height) {
    }
}
