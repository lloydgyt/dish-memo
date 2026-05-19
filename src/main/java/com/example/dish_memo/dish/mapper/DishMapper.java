package com.example.dish_memo.dish.mapper;

import com.example.dish_memo.dish.dto.DishRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * MyBatis mapper for dish_record persistence operations.
 */
@Mapper
public interface DishMapper {

    /**
     * Inserts a new dish record.
     *
     * @param record record to insert
     */
    @Insert("""
            INSERT INTO dish_record (id, user_id, name, file_id, note, date, meal_type, created_at, updated_at)
            VALUES (#{id}, #{userId}, #{name}, #{fileId}, #{note}, #{date}, #{mealType}, #{createdAt}, #{updatedAt})
            """)
    void insert(DishRecord record);

    /**
     * Finds a dish owned by the current user.
     *
     * @param id dish ID
     * @param userId current user ID
     * @return matching owned record or null
     */
    @Select("SELECT * FROM dish_record WHERE id = #{id} AND user_id = #{userId}")
    DishRecord findByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    /**
     * Updates an existing dish record.
     *
     * @param record updated record
     * @return affected row count
     */
    @Update("""
            UPDATE dish_record
            SET name = #{name}, file_id = #{fileId}, note = #{note}, date = #{date},
                meal_type = #{mealType}, updated_at = #{updatedAt}
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int update(DishRecord record);

    /**
     * Deletes a dish owned by the current user.
     *
     * @param id dish ID
     * @param userId current user ID
     * @return affected row count
     */
    @Delete("DELETE FROM dish_record WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    /**
     * Counts dish records matching list filters.
     *
     * @param userId current user ID
     * @param mealType optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional dish name keyword
     * @return matching row count
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM dish_record
            WHERE user_id = #{userId}
            <if test="mealType != null">AND meal_type = #{mealType}</if>
            <if test="dateFrom != null">AND date &gt;= #{dateFrom}</if>
            <if test="dateTo != null">AND date &lt;= #{dateTo}</if>
            <if test="keyword != null and keyword != ''">AND name LIKE CONCAT('%', #{keyword}, '%')</if>
            </script>
            """)
    long countByFilters(
            @Param("userId") String userId,
            @Param("mealType") String mealType,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("keyword") String keyword
    );

    /**
     * Lists dish records matching filters with newest updates first.
     *
     * @param userId current user ID
     * @param mealType optional meal type
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param keyword optional dish name keyword
     * @param limit page size
     * @param offset page offset
     * @return page records
     */
    @Select("""
            <script>
            SELECT *
            FROM dish_record
            WHERE user_id = #{userId}
            <if test="mealType != null">AND meal_type = #{mealType}</if>
            <if test="dateFrom != null">AND date &gt;= #{dateFrom}</if>
            <if test="dateTo != null">AND date &lt;= #{dateTo}</if>
            <if test="keyword != null and keyword != ''">AND name LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DishRecord> listByFilters(
            @Param("userId") String userId,
            @Param("mealType") String mealType,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * Lists recommendation candidates for the current user and meal type.
     *
     * @param userId current user ID
     * @param mealType requested meal type
     * @return candidate records
     */
    @Select("SELECT * FROM dish_record WHERE user_id = #{userId} AND meal_type = #{mealType}")
    List<DishRecord> listByUserIdAndMealType(@Param("userId") String userId, @Param("mealType") String mealType);

    /**
     * Lists today's dishes for a set of friend user IDs.
     *
     * @param userIds friend user IDs
     * @param mealType requested meal type
     * @param date server local date
     * @return matching friend dish records
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM dish_record
            WHERE meal_type = #{mealType}
              AND date = #{date}
              AND user_id IN
              <foreach item="userId" collection="userIds" open="(" separator="," close=")">
                  #{userId}
              </foreach>
            </script>
            """)
    long countTodayDishesByUserIds(
            @Param("userIds") List<String> userIds,
            @Param("mealType") String mealType,
            @Param("date") LocalDate date
    );

    /**
     * Lists today's dishes for a set of friend user IDs with database pagination.
     *
     * @param userIds friend user IDs
     * @param mealType requested meal type
     * @param date server local date
     * @param limit page size
     * @param offset page offset
     * @return matching friend dish records
     */
    @Select("""
            <script>
            SELECT *
            FROM dish_record
            WHERE meal_type = #{mealType}
              AND date = #{date}
              AND user_id IN
              <foreach item="userId" collection="userIds" open="(" separator="," close=")">
                  #{userId}
              </foreach>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DishRecord> listTodayDishesByUserIds(
            @Param("userIds") List<String> userIds,
            @Param("mealType") String mealType,
            @Param("date") LocalDate date,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}
