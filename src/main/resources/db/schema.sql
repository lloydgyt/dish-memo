CREATE DATABASE IF NOT EXISTS dish_memo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE dish_memo;

CREATE TABLE IF NOT EXISTS `user` (
    uid VARCHAR(128) NOT NULL,
    nickname VARCHAR(128) NOT NULL,
    avatar_file_id VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (uid),
    INDEX idx_user_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_relation (
    uid_a VARCHAR(128) NOT NULL,
    uid_b VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (uid_a, uid_b),
    CONSTRAINT chk_friend_relation_not_self CHECK (uid_a <> uid_b),
    UNIQUE KEY uk_friend_relation_pair (uid_a, uid_b),
    INDEX idx_friend_relation_uid_b (uid_b),
    CONSTRAINT fk_friend_relation_uid_a FOREIGN KEY (uid_a) REFERENCES `user` (uid),
    CONSTRAINT fk_friend_relation_uid_b FOREIGN KEY (uid_b) REFERENCES `user` (uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_invitation (
    inviter_uid VARCHAR(128) NOT NULL,
    expire_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (inviter_uid),
    INDEX idx_friend_invitation_inviter_uid (inviter_uid),
    INDEX idx_friend_invitation_expire_at (expire_at),
    CONSTRAINT fk_friend_invitation_inviter FOREIGN KEY (inviter_uid) REFERENCES `user` (uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dish_record (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    file_id VARCHAR(512) NOT NULL,
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
