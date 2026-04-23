CREATE DATABASE IF NOT EXISTS dish_memo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE dish_memo;

CREATE TABLE IF NOT EXISTS dish_record (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    note TEXT NULL,
    date DATE NOT NULL,
    meal_type VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT chk_dish_record_meal_type CHECK (meal_type IN ('breakfast', 'lunch', 'dinner')),
    INDEX idx_dish_record_user_meal (user_id, meal_type),
    INDEX idx_dish_record_user_date (user_id, date),
    INDEX idx_dish_record_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
