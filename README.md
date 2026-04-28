# cook-history-service

Spring Boot 后端服务，提供个人菜品记录 CRUD、菜名建议和“今天吃什么”随机推荐能力。接口版本为 `v1.1.0`，详细契约见 [docs/api_doc.md](/home/lloydgyt/dish-memo/docs/api_doc.md)。

## 模块

| 模块 | 职责 |
|---|---|
| `common` | 统一响应、错误码、业务异常、全局异常处理、`X-User-Id` 校验。 |
| `dish` | 菜品新增、列表、详情、编辑、删除、用户隔离和 MyBatis 数据访问。 |
| `suggestion` | 基于对象存储 `file_id` 的菜名建议，包含模型失败降级语义。 |
| `recommendation` | 按餐别随机返回不重复历史菜品候选。 |

后端不再提供图片上传接口，也不保存或返回 `image_url`。图片由前端直传对象存储，后端仅保存并返回对象存储 `file_id`。

## 接口

Base URL:

```text
http://localhost:8080/api/v1
```

所有业务接口都需要 Header：

```text
X-User-Id: <current-user-id>
```

当前实现的接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/dishes/name-suggestions` | 基于 `file_id` 生成菜名建议。 |
| `POST` | `/dishes` | 新增菜品记录。 |
| `GET` | `/dishes` | 分页查询菜品记录。 |
| `GET` | `/dishes/{dish_id}` | 查询菜品详情。 |
| `PUT` | `/dishes/{dish_id}` | 编辑菜品记录。 |
| `DELETE` | `/dishes/{dish_id}` | 删除菜品记录。 |
| `GET` | `/recommendations/today-meals` | 获取“今天吃什么”推荐。 |

## 数据库

建库建表脚本位于 [src/main/resources/db/schema.sql](/home/lloydgyt/dish-memo/src/main/resources/db/schema.sql)，单表建表语句也同步放在 [docs/dish_record_schema.sql](/home/lloydgyt/dish-memo/docs/dish_record_schema.sql)。

初始化：

```bash
mysql -uroot -p < src/main/resources/db/schema.sql
```

核心表 `dish_record` 字段与 API 文档一致，图片字段为 `file_id`：

```sql
CREATE TABLE IF NOT EXISTS dish_record (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(50) NOT NULL,
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
```

## 配置

配置文件为 [src/main/resources/application.yml](/home/lloydgyt/dish-memo/src/main/resources/application.yml)。

必需环境变量：

```bash
export DB_ADDRESS='localhost:3306'
export DB_USERNAME=root
export DB_PASSWORD='your_password'
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_DATABASE=0
export SERVER_PORT=8080
```

Jackson 已配置为 `SNAKE_CASE`，API JSON 字段使用 `file_id`、`meal_type`、`created_at`、`updated_at`、`page_no`、`page_size`、`requested_size`、`actual_size`、`is_empty`、`empty_tip` 等文档格式。

## 测试与构建

```bash
mvn test
mvn package
```

## 启动

```bash
mvn spring-boot:run
```

## 日志

日志配置文件为 [src/main/resources/logback.xml](/home/lloydgyt/dish-memo/src/main/resources/logback.xml)。用户 API 与核心 Service 使用 `slf4j` 输出 JSON 结构化日志：

- `DishService`、`RecommendationService`、`SuggestionService` 的业务入口输出 `INFO`。
- `GlobalExceptionHandler` 的异常分支输出 `WARN`。
- 日志包含 `userId` 和 `description`；异常日志额外包含 `exceptionType` 和 `exceptionMessage`。
- 日志工具会脱敏常见 `password`、`token`、`authorization`、`access_token`、`refresh_token` 明文值。
