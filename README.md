# cook-history-service

Spring Boot 后端服务，提供个人菜品记录 CRUD、基于阿里云百炼多模态模型的菜名建议和“今天吃什么”随机推荐能力。接口版本为 `v1.1.0`，详细契约见 [docs/api_doc.md](/home/lloydgyt/dish-memo/docs/api_doc.md)。

## 模块

| 模块 | 职责 |
|---|---|
| `common` | 统一响应、错误码、业务异常、全局异常处理、`X-WX-OPENID` 校验。 |
| `dish` | 菜品新增、列表、详情、编辑、删除、用户隔离和 MyBatis 数据访问。 |
| `suggestion` | 基于对象存储临时 `image_url` 与 `prompt` 调用阿里云百炼 `qwen3.6-flash` 生成菜名建议。 |
| `recommendation` | 按餐别随机返回不重复历史菜品候选。 |

后端不再提供图片上传接口，菜品记录也不保存或返回 `image_url`。图片由前端直传对象存储，新增/编辑菜品记录时后端仅保存并返回对象存储 `file_id`；生成菜名建议时前端传入对象存储临时 `image_url` 供模型识别。

## 接口

Base URL:

```text
http://localhost:8080/api/v1
```

所有业务接口都需要 Header：

```text
X-WX-OPENID: <current-user-openid>
```

服务端严格以 `X-WX-OPENID` 作为 `dish_record.user_id` 的数据路由依据。菜品查询、详情、编辑、删除和推荐候选查询都会在数据库访问前注入当前 OpenID 过滤条件；旧的 `X-User-Id` 不再作为身份来源。

当前实现的接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/dishes/name-suggestions` | 基于 `image_url` 与 `prompt` 生成菜名建议。 |
| `POST` | `/dishes` | 新增菜品记录。 |
| `GET` | `/dishes` | 分页查询菜品记录。 |
| `GET` | `/dishes/{dish_id}` | 查询菜品详情。 |
| `PUT` | `/dishes/{dish_id}` | 编辑菜品记录。 |
| `DELETE` | `/dishes/{dish_id}` | 删除菜品记录。 |
| `GET` | `/recommendations/today-meals` | 获取“今天吃什么”推荐。 |

## 数据库

建库建表脚本位于 [src/main/resources/db/schema.sql](/home/lloydgyt/dish-memo/src/main/resources/db/schema.sql)。

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
export SERVER_PORT=8080
export DB_ADDRESS='localhost:3306'
export DB_USERNAME=root
export DB_PASSWORD='your_password'
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_DATABASE=0
export BAILIAN_API_KEY='your-bailian-api-key'
export BAILIAN_BASE_URL='https://dashscope.aliyuncs.com/compatible-mode/v1'
export BAILIAN_MODEL='qwen3.6-flash'
export SUGGESTION_IMAGE_URL_ALLOWED_HOSTS='oss.example.com,img.example.com,7072-prod-d5gdc5h99b1442a27-1424479475.tcb.qcloud.la'
```

Jackson 已配置为 `SNAKE_CASE`，API JSON 字段使用 `image_url`、`suggested_name`、`model_status`、`file_id`、`meal_type`、`created_at`、`updated_at`、`page_no`、`page_size`、`requested_size`、`actual_size`、`is_empty`、`empty_tip` 等文档格式。

`/dishes/name-suggestions` 请求体：

```json
{
  "image_url": "https://oss.example.com/temp/dish_01.jpg",
  "prompt": "请推荐一个简洁的家常中文菜名"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "suggested_name": "番茄炒蛋",
    "model_status": "success",
    "reason": null
  }
}
```

该接口只接受 `https` 图片 URL，域名必须命中 `SUGGESTION_IMAGE_URL_ALLOWED_HOSTS`。参数错误返回 `4001001`；模型网络或图片访问失败返回 `5001002`；模型响应无法解析返回 `4221001`。

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

日志配置文件为 [src/main/resources/logback.xml](/home/lloydgyt/dish-memo/src/main/resources/logback.xml)。`/api/v1/**` 请求在 Controller 入口由拦截器统一输出一条 JSON 结构化请求日志：

- 请求日志字段固定包含 `request_id`、`user_id`、`request_params`、`method`、`path`、`status`、`duration_ms`。
- `request_id` 优先读取 `X-Request-Id`，缺失时自动生成 UUID。
- `user_id` 读取 `X-WX-OPENID`，缺失时记录为 `UNKNOWN`。
- `request_params` 会在输出前脱敏，命中 `password`、`token`、`authorization`、`access_token`、`refresh_token` 等敏感参数名时值固定为 `[REDACTED]`。
- `GlobalExceptionHandler` 的异常分支仍输出 `WARN` 结构化日志，包含 `userId`、`description`、`exceptionType` 和已脱敏的 `exceptionMessage`。
