package com.example.dish_memo.dish.service;

import com.example.dish_memo.common.BusinessException;
import com.example.dish_memo.common.ErrorCode;
import com.example.dish_memo.dish.dto.DeleteDishResponse;
import com.example.dish_memo.dish.dto.CreateDishResponse;
import com.example.dish_memo.dish.dto.DishListItemResponse;
import com.example.dish_memo.dish.dto.DishPageResponse;
import com.example.dish_memo.dish.dto.DishRecord;
import com.example.dish_memo.dish.dto.DishRequest;
import com.example.dish_memo.dish.dto.DishResponse;
import com.example.dish_memo.dish.dto.UpdateDishResponse;
import com.example.dish_memo.dish.dto.MealType;
import com.example.dish_memo.dish.mapper.DishMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Business service for dish CRUD and user ownership validation.
 */
@Service
public class DishService {
    private static final Pattern FILE_ID_PATTERN = Pattern.compile(
            "^(cloud://.+|(?:development|production)/dish/[^/\\s]+/[^\\s]+)$"
    );

    private final DishMapper dishMapper;

    /**
     * Creates the service with its persistence dependency.
     *
     * @param dishMapper MyBatis dish mapper
     */
    public DishService(DishMapper dishMapper) {
        this.dishMapper = dishMapper;
    }

    /**
     * Creates a dish record owned by the current user.
     *
     * @param userId current user ID
     * @param request validated create request
     * @return created dish response
     */
    public CreateDishResponse create(String userId, DishRequest request) {
        MealType mealType = MealType.from(request.mealType());
        LocalDateTime now = LocalDateTime.now();
        DishRecord record = new DishRecord();
        record.setId("dish_" + UUID.randomUUID().toString().replace("-", ""));
        record.setUserId(userId);
        fillRecord(record, request, mealType, now);
        record.setCreatedAt(now);
        dishMapper.insert(record);
        return CreateDishResponse.from(record);
    }

    /**
     * Returns a paginated and filtered dish list for the current user.
     *
     * @param userId current user ID
     * @param pageNo requested page number
     * @param pageSize requested page size
     * @param mealType raw optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional dish name keyword
     * @return paginated response
     */
    public DishPageResponse list(
            String userId,
            int pageNo,
            int pageSize,
            String mealType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String keyword
    ) {
        validatePage(pageNo, pageSize);
        String parsedMealType = StringUtils.hasText(mealType) ? MealType.from(mealType).name() : null;
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "date_from must be before or equal to date_to");
        }
        if (keyword != null && keyword.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "keyword length must be <= 50");
        }

        int offset = (pageNo - 1) * pageSize;
        long total = dishMapper.countByFilters(userId, parsedMealType, dateFrom, dateTo, keyword);
        List<DishListItemResponse> list = dishMapper.listByFilters(
                        userId, parsedMealType, dateFrom, dateTo, keyword, pageSize, offset
                ).stream()
                .map(DishListItemResponse::from)
                .toList();
        return new DishPageResponse(list, total, pageNo, pageSize);
    }

    /**
     * Returns one dish after checking not-found and user ownership semantics.
     *
     * @param userId current user ID
     * @param dishId requested dish ID
     * @return full dish response
     */
    public DishResponse get(String userId, String dishId) {
        return DishResponse.from(requireOwnedRecord(userId, dishId));
    }

    /**
     * Updates a dish owned by the current user.
     *
     * @param userId current user ID
     * @param dishId requested dish ID
     * @param request validated update request
     * @return updated dish response
     */
    public UpdateDishResponse update(String userId, String dishId, DishRequest request) {
        DishRecord record = requireOwnedRecord(userId, dishId);
        MealType mealType = MealType.from(request.mealType());
        fillRecord(record, request, mealType, LocalDateTime.now());
        dishMapper.update(record);
        return UpdateDishResponse.from(record);
    }

    /**
     * Deletes a dish owned by the current user.
     *
     * @param userId current user ID
     * @param dishId requested dish ID
     * @return delete response
     */
    public DeleteDishResponse delete(String userId, String dishId) {
        requireOwnedRecord(userId, dishId);
        dishMapper.deleteByIdAndUserId(dishId, userId);
        return new DeleteDishResponse(true);
    }

    /**
     * Lists all recommendation candidates for one user and meal type.
     *
     * @param userId current user ID
     * @param mealType requested meal type
     * @return candidate dish records
     */
    public List<DishRecord> listRecommendationCandidates(String userId, String mealType) {
        MealType parsedMealType = MealType.from(mealType);
        return dishMapper.listByUserIdAndMealType(userId, parsedMealType.name());
    }

    private void fillRecord(DishRecord record, DishRequest request, MealType mealType, LocalDateTime updatedAt) {
        String fileId = normalizeFileId(request.fileId());
        record.setName(request.name().trim());
        record.setFileId(fileId);
        record.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        record.setDate(request.date());
        record.setMealType(mealType.name());
        record.setUpdatedAt(updatedAt);
    }

    /**
     * Normalizes and validates object storage file IDs accepted by the API contract.
     *
     * @param fileId raw file ID from the request body
     * @return trimmed file ID
     */
    private String normalizeFileId(String fileId) {
        String trimmed = fileId == null ? "" : fileId.trim();
        if (!FILE_ID_PATTERN.matcher(trimmed).matches() || trimmed.contains("..")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file_id is invalid");
        }
        return trimmed;
    }

    private DishRecord requireOwnedRecord(String userId, String dishId) {
        DishRecord record = dishMapper.findById(dishId);
        if (record == null) {
            throw new BusinessException(ErrorCode.DISH_NOT_FOUND, "dish record not found");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "forbidden to access this dish record");
        }
        return record;
    }

    private void validatePage(int pageNo, int pageSize) {
        if (pageNo <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page_no must be greater than 0");
        }
        if (pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page_size must be between 1 and 100");
        }
    }
}
