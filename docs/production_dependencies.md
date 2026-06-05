# 生产环境依赖清单

本项目是 Spring Boot 3.3.4 后端服务。当前自动安装脚本按 Ubuntu 24.04 LTS 生产机器维护。

## 必需依赖

| 依赖 | 建议版本 | 用途 |
|---|---:|---|
| Java JDK/JRE | 17 | 运行 Spring Boot 应用；若在生产机上构建 jar，也需要 JDK。 |
| Maven | 3.8+，Dockerfile 使用 3.9.9 | 构建 jar。项目也带有 `mvnw`，但生产机直接安装 Maven 更便于排障。 |
| MySQL Server | 8.0+ | 业务数据库，应用通过 `mysql-connector-j` 连接。 |
| Redis Server | 6.x+ / 7.x | Spring Data Redis 运行依赖，配置项在 `spring.data.redis`。 |
| ca-certificates | 系统包 | 调用 HTTPS 外部服务，例如阿里云百炼模型接口。 |
| curl | 系统包 | 健康检查、接口验证、下载诊断。 |
| tzdata | 系统包 | 设置生产机器时区，建议使用 `Asia/Shanghai` 或业务统一时区。 |

## 运行配置

应用启动前需要准备这些环境变量：

```bash
export SERVER_PORT=8080
export DB_ADDRESS='localhost:3306'
export DB_SCHEMA='dish_memo'
export DB_USERNAME='root'
export DB_PASSWORD='your_password'
export REDIS_HOST='localhost'
export REDIS_PORT=6379
export REDIS_DATABASE=0
export BAILIAN_API_KEY='your-bailian-api-key'
export BAILIAN_BASE_URL='https://dashscope.aliyuncs.com/compatible-mode/v1'
export BAILIAN_MODEL='qwen3.6-flash'
export SUGGESTION_IMAGE_URL_ALLOWED_HOSTS='oss.example.com,img.example.com'
```

## 数据库初始化

部署前需要创建业务库和表。当前 README 指向：

```bash
mysql -uroot -p < src/main/resources/db/schema.sql
```

如果使用独立 DDL，也可以按项目文档维护 `docs/table_ddl/schema.sql`。生产环境建议先在测试库验证 DDL，再执行到正式库。

## 自动安装脚本

安装依赖：

```bash
sudo bash scripts/install_production_dependencies.sh
```

脚本仅支持 Ubuntu 24.04 LTS，使用系统默认 `apt` 仓库安装依赖。其中 MySQL 使用 Ubuntu 24.04 默认仓库的 `mysql-server` 包。

可选跳过项：

```bash
sudo INSTALL_MYSQL=0 bash scripts/install_production_dependencies.sh
sudo INSTALL_REDIS=0 bash scripts/install_production_dependencies.sh
sudo INSTALL_MAVEN=0 bash scripts/install_production_dependencies.sh
```

脚本不会自动配置数据库用户、密码、防火墙规则、系统服务文件或应用启动参数。这些属于生产环境安全配置，应按机器实际情况单独处理。
