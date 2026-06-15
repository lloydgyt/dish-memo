package com.example.dish_memo.testing.controller;

import com.example.dish_memo.common.ApiHeaders;
import com.example.dish_memo.common.ApiResponse;
import com.example.dish_memo.dish.dto.DishPageResponse;
import com.example.dish_memo.testing.service.TestingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Baseline testing endpoint that mirrors the dish list API without database access.
 */
@RestController
@RequestMapping("/api/v1/testing")
public class TestingController {
    private final TestingService testingService;

    public TestingController(TestingService testingService) {
        this.testingService = testingService;
    }

    /**
     * Lists fixed in-memory dish records with the same request and response shape as GET /api/v1/dishes.
     *
     * @param userId current OpenID from gateway header
     * @param pageNo page number
     * @param pageSize page size
     * @param mealType optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional name keyword
     * @return paginated dish list response
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
        return ApiResponse.ok(testingService.list(userId, pageNo, pageSize, mealType, dateFrom, dateTo, keyword));
    }
}
