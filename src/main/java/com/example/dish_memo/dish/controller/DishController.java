package com.example.dish_memo.dish.controller;

import com.example.dish_memo.common.ApiResponse;
import com.example.dish_memo.common.ApiHeaders;
import com.example.dish_memo.dish.dto.CreateDishResponse;
import com.example.dish_memo.dish.dto.DeleteDishResponse;
import com.example.dish_memo.dish.dto.DishPageResponse;
import com.example.dish_memo.dish.dto.DishRequest;
import com.example.dish_memo.dish.dto.DishResponse;
import com.example.dish_memo.dish.dto.UpdateDishResponse;
import com.example.dish_memo.dish.service.DishService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * HTTP API controller for dish record CRUD endpoints.
 */
@RestController
@RequestMapping("/api/v1/dishes")
public class DishController {
    private final DishService dishService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param dishService dish business service
     */
    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    /**
     * Creates a dish record for the current user.
     *
     * @param userId current OpenID from gateway header
     * @param request create request
     * @return created dish response
     */
    @PostMapping
    public ApiResponse<CreateDishResponse> create(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @Valid @RequestBody DishRequest request
    ) {
        return ApiResponse.ok(dishService.create(userId, request));
    }

    /**
     * Lists current user's dishes with pagination and filters.
     *
     * @param userId current OpenID from gateway header
     * @param pageNo page number
     * @param pageSize page size
     * @param mealType optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional name keyword
     * @return paginated dish list
     */
    @GetMapping
    public ApiResponse<DishPageResponse> list(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "meal_type", required = false) String mealType,
            @RequestParam(name = "date_from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(name = "date_to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return ApiResponse.ok(dishService.list(userId, pageNo, pageSize, mealType, dateFrom, dateTo, keyword));
    }

    /**
     * Gets one dish record for the current user.
     *
     * @param userId current OpenID from gateway header
     * @param dishId requested dish ID
     * @return dish detail
     */
    @GetMapping("/{dish_id}")
    public ApiResponse<DishResponse> get(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @PathVariable("dish_id") String dishId
    ) {
        return ApiResponse.ok(dishService.get(userId, dishId));
    }

    /**
     * Updates one dish record for the current user.
     *
     * @param userId current OpenID from gateway header
     * @param dishId requested dish ID
     * @param request update request
     * @return updated dish
     */
    @PutMapping("/{dish_id}")
    public ApiResponse<UpdateDishResponse> update(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @PathVariable("dish_id") String dishId,
            @Valid @RequestBody DishRequest request
    ) {
        return ApiResponse.ok(dishService.update(userId, dishId, request));
    }

    /**
     * Deletes one dish record for the current user.
     *
     * @param userId current OpenID from gateway header
     * @param dishId requested dish ID
     * @return delete result
     */
    @DeleteMapping("/{dish_id}")
    public ApiResponse<DeleteDishResponse> delete(
            @RequestHeader(ApiHeaders.WX_OPENID) String userId,
            @PathVariable("dish_id") String dishId
    ) {
        return ApiResponse.ok(dishService.delete(userId, dishId));
    }
}
