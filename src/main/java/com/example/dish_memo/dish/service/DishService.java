package com.example.dish_memo.dish.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Business service for dish CRUD and user ownership validation.
 */
@Service
public class DishService {
    private static final Logger log = LoggerFactory.getLogger(DishService.class);
    private static final Duration DISH_LIST_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<DishRecord>> DISH_RECORD_LIST_TYPE = new TypeReference<>() {
    };
    private static final String COUNT_CACHE_KEY_PREFIX = "dish:list:count:v1:";
    private static final String LIST_CACHE_KEY_PREFIX = "dish:list:page:v1:";
    private static final int RECOMMENDATION_PAGE_SIZE = 100;
    private static final Pattern FILE_ID_PATTERN = Pattern.compile(
            "^(cloud://.+|(?:development|production)/dish/[^/\\s]+/[^\\s]+)$"
    );

    private final DishMapper dishMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Cache<String, String> localListCache;

    /**
     * Creates the service with persistence and cache dependencies.
     *
     * @param dishMapper MyBatis dish mapper
     */
    public DishService(DishMapper dishMapper, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.dishMapper = dishMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.localListCache = Caffeine.newBuilder()
                .expireAfterWrite(DISH_LIST_CACHE_TTL)
                .maximumSize(10_000)
                .build();
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
        evictUnfilteredListCache(userId);
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
        boolean cacheable = isUnfilteredListQuery(parsedMealType, dateFrom, dateTo, keyword);
        long total = cacheable
                ? countByFiltersWithCache(userId)
                : dishMapper.countByFilters(userId, parsedMealType, dateFrom, dateTo, keyword);
        List<DishRecord> records = cacheable
                ? listByFiltersWithCache(userId, pageSize, offset)
                : dishMapper.listByFilters(userId, parsedMealType, dateFrom, dateTo, keyword, pageSize, offset);
        List<DishListItemResponse> list = records.stream()
                .map(DishListItemResponse::from)
                .toList();
        return new DishPageResponse(list, total, pageNo, pageSize);
    }

    /**
     * Returns one dish after applying user-level query isolation.
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
        evictUnfilteredListCache(userId);
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
        evictUnfilteredListCache(userId);
        return new DeleteDishResponse(true);
    }

    /**
     * Counts recommendation candidates for one user and meal type.
     *
     * @param userId current user ID
     * @param mealType requested meal type
     * @return candidate count
     */
    public long countRecommendationCandidates(String userId, String mealType) {
        MealType parsedMealType = MealType.from(mealType);
        return dishMapper.countByUserIdAndMealType(userId, parsedMealType.name());
    }

    /**
     * Lists one fixed-size recommendation candidate page for one user and meal type.
     *
     * @param userId current user ID
     * @param mealType requested meal type
     * @param pageNo requested page number
     * @return candidate dish records
     */
    public List<DishRecord> listRecommendationCandidatePage(String userId, String mealType, int pageNo) {
        MealType parsedMealType = MealType.from(mealType);
        if (pageNo <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page_no must be greater than 0");
        }
        int offset = (pageNo - 1) * RECOMMENDATION_PAGE_SIZE;
        return dishMapper.listRecommendationPageByUserIdAndMealType(userId, parsedMealType.name(), offset);
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
        DishRecord record = dishMapper.findByIdAndUserId(dishId, userId);
        if (record == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "forbidden to access this dish record");
        }
        return record;
    }

    private long countByFiltersWithCache(String userId) {
        String key = countCacheKey(userId);
        String localCached = localListCache.getIfPresent(key);
        if (localCached != null) {
            try {
                return Long.parseLong(localCached);
            } catch (RuntimeException ex) {
                localListCache.invalidate(key);
                log.warn("Failed to read local dish count cache for user {}", userId, ex);
            }
        }

        ValueOperations<String, String> values = redisTemplate.opsForValue();
        try {
            String cached = values.get(key);
            if (cached != null) {
                long total = Long.parseLong(cached);
                localListCache.put(key, cached);
                return total;
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to read dish count cache for user {}", userId, ex);
        }

        long total = dishMapper.countByFilters(userId, null, null, null, null);
        localListCache.put(key, Long.toString(total));
        try {
            values.set(key, Long.toString(total), DISH_LIST_CACHE_TTL);
        } catch (RuntimeException ex) {
            log.warn("Failed to write dish count cache for user {}", userId, ex);
        }
        return total;
    }

    private List<DishRecord> listByFiltersWithCache(String userId, int pageSize, int offset) {
        String key = listCacheKey(userId, pageSize, offset);
        String localCached = localListCache.getIfPresent(key);
        if (localCached != null) {
            try {
                return objectMapper.readValue(localCached, DISH_RECORD_LIST_TYPE);
            } catch (JsonProcessingException | RuntimeException ex) {
                localListCache.invalidate(key);
                log.warn("Failed to read local dish list cache for user {}", userId, ex);
            }
        }

        ValueOperations<String, String> values = redisTemplate.opsForValue();
        try {
            String cached = values.get(key);
            if (cached != null) {
                List<DishRecord> records = objectMapper.readValue(cached, DISH_RECORD_LIST_TYPE);
                localListCache.put(key, cached);
                return records;
            }
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Failed to read dish list cache for user {}", userId, ex);
        }

        List<DishRecord> records = dishMapper.listByFilters(userId, null, null, null, null, pageSize, offset);
        try {
            String serializedRecords = objectMapper.writeValueAsString(records);
            localListCache.put(key, serializedRecords);
            values.set(key, serializedRecords, DISH_LIST_CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Failed to write dish list cache for user {}", userId, ex);
        }
        return records;
    }

    private void evictUnfilteredListCache(String userId) {
        evictLocalUnfilteredListCache(userId);
        try {
            redisTemplate.delete(countCacheKey(userId));
            Set<String> listKeys = scanListCacheKeys(userId);
            if (listKeys != null && !listKeys.isEmpty()) {
                redisTemplate.delete(listKeys);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to evict dish list cache for user {}", userId, ex);
        }
    }

    private void evictLocalUnfilteredListCache(String userId) {
        String userListKeyPrefix = LIST_CACHE_KEY_PREFIX + userHash(userId) + ":";
        localListCache.invalidate(countCacheKey(userId));
        localListCache.asMap().keySet().removeIf(key -> key.startsWith(userListKeyPrefix));
    }

    private Set<String> scanListCacheKeys(String userId) {
        String pattern = LIST_CACHE_KEY_PREFIX + userHash(userId) + ":*";
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                cursor.forEachRemaining(key -> keys.add(new String(key, StandardCharsets.UTF_8)));
            }
            return keys;
        });
    }

    private boolean isUnfilteredListQuery(String parsedMealType, LocalDate dateFrom, LocalDate dateTo, String keyword) {
        return parsedMealType == null && dateFrom == null && dateTo == null && keyword == null;
    }

    private String countCacheKey(String userId) {
        return COUNT_CACHE_KEY_PREFIX + userHash(userId);
    }

    private String listCacheKey(String userId, int pageSize, int offset) {
        return LIST_CACHE_KEY_PREFIX + userHash(userId) + ":" + pageSize + ":" + offset;
    }

    private String userHash(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(userId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
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
