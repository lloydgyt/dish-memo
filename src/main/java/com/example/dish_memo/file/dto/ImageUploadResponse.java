package com.example.dish_memo.file.dto;

/**
 * Response returned after storing an uploaded dish image.
 *
 * @param fileId generated file ID
 * @param imageUrl public URL for subsequent dish creation
 * @param width image width in pixels
 * @param height image height in pixels
 */
public record ImageUploadResponse(String fileId, String imageUrl, int width, int height) {
}
