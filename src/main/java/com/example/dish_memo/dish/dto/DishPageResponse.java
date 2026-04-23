package com.example.dish_memo.dish.dto;

import java.util.List;

/**
 * Paginated dish list response.
 *
 * @param list current page records
 * @param total total records matching filters
 * @param pageNo current page number
 * @param pageSize current page size
 */
public record DishPageResponse(
        List<DishListItemResponse> list,
        long total,
        int pageNo,
        int pageSize
) {
}
