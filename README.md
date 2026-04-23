# Backend

Spring Boot 后端工程，服务名为 `cook-history-service`，接口版本为 `v1.0.0`。

## 模块

| 模块 | 职责 |
|---|---|
| `common` | 统一响应、错误码、业务异常、全局异常处理、`X-User-Id` 校验。 |
| `dish` | 菜品 CRUD、分页查询、用户隔离和 MyBatis 数据访问。 |
| `file` | 图片上传，本地存储 MVP 实现。 |
| `suggestion` | 菜名建议，包含模型失败降级语义。 |
| `recommendation` | “今天吃什么”随机推荐。 |

## 配置

配置文件为 `src/main/resources/application.yml`。数据库、Redis、端口和上传目录均支持环境变量覆盖，在启动时指定

## 日志配置

日志配置文件为 `src/main/resources/logback.xml`。该文件是独立标准 Logback XML，兼容 Spring Boot 2.x、Spring Boot 3.x 和普通 JVM 环境；不使用 Spring Boot 专属标签，不新增依赖，不配置自定义 Appender 或 AsyncAppender。

内置 Appender：

- `CONSOLE`：实时输出到控制台。
- `ROLLING_FILE`：写入本地文件，并通过 `SizeAndTimeBasedRollingPolicy` 按日期和大小滚动归档。

默认输出路径为 `./logs/cook-history-service.log`，归档路径为 `./logs/archive/`。目录不存在时 Logback 会尝试自动创建；如果目录无写权限，文件 Appender 会输出启动 `ERROR` 状态，控制台日志仍可继续使用。

可覆盖参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `APP_NAME` | `cook-history-service` | 应用名，也作为默认日志文件名前缀 |
| `LOG_PATH` | `./logs` | 日志输出目录 |
| `LOG_FILE_NAME` | `${APP_NAME}` | 日志文件名，不含扩展名 |
| `LOG_CHARSET` | `UTF-8` | 日志编码 |
| `LOG_PATTERN` | 标准文本 pattern | 控制台日志格式 |
| `LOG_FILE_PATTERN` | 标准文本 pattern | 文件日志格式 |
| `LOG_LEVEL_ROOT` | `INFO` | root logger 级别 |
| `LOG_LEVEL_APP` | `INFO` | `com.example.dish_memo` logger 级别 |
| `LOG_LEVEL_SPRING` | `INFO` | Spring 框架 logger 级别 |
| `LOG_LEVEL_MYBATIS` | `INFO` | MyBatis logger 级别 |
| `LOG_LEVEL_HIBERNATE` | `WARN` | Hibernate logger 级别 |
| `LOG_MAX_FILE_SIZE` | `100MB` | 单个归档文件最大体积 |
| `LOG_MAX_HISTORY` | `30` | 归档保留天数 |
| `LOG_TOTAL_SIZE_CAP` | `10GB` | 归档总容量上限 |

环境变量示例：

```bash
export LOG_PATH=/var/log/cook-history-service
export LOG_FILE_NAME=cook-history-service
export LOG_LEVEL_ROOT=INFO
export LOG_LEVEL_APP=DEBUG
export LOG_MAX_FILE_SIZE=50MB
export LOG_MAX_HISTORY=14
export LOG_TOTAL_SIZE_CAP=2GB
```

JVM 参数示例：

```bash
java \
  -DLOG_PATH=/var/log/cook-history-service \
  -DLOG_LEVEL_ROOT=INFO \
  -DLOG_MAX_FILE_SIZE=50MB \
  -DLOG_MAX_HISTORY=14 \
  -DLOG_TOTAL_SIZE_CAP=2GB \
  -jar target/cook-history-service-1.0.0.jar
```

一键替换或外置加载：

```bash
# 备份已有配置
cp src/main/resources/logback.xml src/main/resources/logback.xml.bak.$(date +%Y%m%d%H%M%S)

# 使用项目内配置启动
mvn spring-boot:run

# 使用外置配置启动
java -Dlogback.configurationFile=/path/to/logback.xml -jar target/cook-history-service-1.0.0.jar
```

启动后检查控制台输出，并确认 `${LOG_PATH}` 目录下生成当前日志文件和 `archive/` 归档目录。

## 数据库初始化

```bash
mysql -uroot -p < src/main/resources/db/schema.sql
```

## 测试与构建

```bash
mvn test
mvn package
```

## 启动

```bash
export DB_ADDRESS='localhost:3306'
export DB_USERNAME=root
export DB_PASSWORD={{db_password}}
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_DATABASE=0
export SERVER_PORT=8080
export UPLOAD_BASE_DIR=uploads
export UPLOAD_PUBLIC_PREFIX="http://localhost:8080/uploads"
mvn spring-boot:run
```

上传图片会保存到 `${UPLOAD_BASE_DIR}/dish/`，上传接口返回的公开地址为 `${UPLOAD_PUBLIC_PREFIX}/dish/{filename}`。服务端只将 `/uploads/dish/**` 映射到该物理目录，不暴露上传根目录下的其他路径；静态图片请求会拒绝 `../`、`..\\` 和 URL 编码后的路径穿越形式，并且只允许 `jpg`、`jpeg`、`png`、`webp` 扩展名，其他扩展名返回 HTTP `404`。


启动后 API Base URL：

```text
http://localhost:8080/api/v1
```

所有业务接口都需要 `X-User-Id` Header。

## 结构化日志

用户 API 与核心 Service 使用 `slf4j` 输出 JSON 结构化日志：

- `DishService`、`RecommendationService`、`SuggestionService`、`FileStorageService` 的业务入口输出 `INFO`。
- `GlobalExceptionHandler` 的所有异常分支输出 `WARN`；Service 内已有显式 `catch` 分支也输出 `WARN`。
- 所有日志强制包含 `userId` 和 `description`；异常日志额外包含 `exceptionType` 和 `exceptionMessage`。
- `userId` 来自 `X-User-Id`，异常处理阶段缺失时使用 `UNKNOWN`。
- 日志工具会脱敏常见 `password`、`token`、`authorization`、`access_token`、`refresh_token` 明文值，业务代码不记录完整 Token、密码或完整请求体。
