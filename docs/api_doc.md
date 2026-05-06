## 做过的菜记录服务 API 文档

- **服务名称**：cook-history-service

---

## 1. 服务信息

### 1.1 服务说明
提供个人菜品记录的新增、查看、编辑、删除，基于历史记录的“今天吃什么”随机推荐能力，以及基于图片和提示词的菜品名称推荐能力。

### 1.2 Base URL

| 环境 | Base URL |
|---|---|
| development | `http://localhost:8080/api/v1` |
| production | 微信云容器调用，id：prod-d5gdc5h99b1442a27 |

---

## 2. 通用约定

### 2.1 鉴权方式

所有业务接口均要求登录。MVP 默认由网关解析微信登录态，并向服务透传用户身份：

| Header | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `X-Request-Id` | string | 否 | 请求链路追踪 ID |
| `X-WX-OPENID` | string | 是 | 微信 openid（作为后续操作的 user_id），生产环境下由微信云托管注入，开发环境下由前端注入 |

> 服务端以 `X-WX-OPENID` 作为数据隔离依据，任何查询、编辑、删除、推荐操作均仅作用于当前用户自己的菜品数据。

### 2.2 通用响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

| 字段名 | 类型 | 说明 |
|---|---|---|
| code | integer | 业务码，`0` 表示成功 |
| message | string | 响应消息 |
| data | object/null | 响应数据 |

### 2.3 通用规则

- 空数组返回 `[]`
- 无值字段返回 `null`
- 分页默认：`page_no=1`，`page_size=20`
- 时间字段统一使用 `YYYY-MM-DD` 或 RFC3339 时间格式

### 2.4 数据库表结构

#### DishRecord（目前只有一个表）

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| user_id | string | 用户 ID |
| name | string | 菜品名称 |
| file_id | string | 菜品图片在对象存储中的文件 ID |
| note | string/null | 备注 |
| date | string | 做菜/食用日期，格式 `YYYY-MM-DD` |
| meal_type | string | 餐别，取值 breakfast, lunch, dinner |
| created_at | string | 创建时间，RFC3339 |
| updated_at | string | 更新时间，RFC3339 |

### 2.5 图片存储约定

图片文件由前端直接上传到对象存储，后端不接收图片文件、不保存图片 URL，仅保存对象存储返回的 `file_id`。

上传目录按运行环境区分：

| 环境 | 对象存储目录前缀 | 说明 |
|---|---|---|
| development | `development/` | 开发环境上传图片 |
| production | `production/` | 生产环境上传图片 |

前端上传完成后，将对象存储返回的 `fileID` 作为后端 API 中的 `file_id` 提交。该 `file_id` 必须是前端可直接用于从对象存储下载或展示图片的文件 ID。

对象存储路径格式：

```text
{environment}/dish/{user_id}/{file_name}
```

示例：

```text
production/dish/u_1001/img_01HRXYZ.jpg
development/dish/u_1001/img_01HRXYZ.jpg
```

> 说明：若对象存储 SDK 返回完整 `cloud://.../production/...` 或类似格式，应将完整值作为 `file_id` 传给后端。后端可对 `file_id` 做非空、格式、当前运行环境前缀校验，以及必要的用户归属校验；后端响应中不返回 `image_url`，也不负责拼接图片访问 URL。

### 2.6 外部配置

配置 MySQL、Redis、阿里云百炼：

- 生产环境通过 Dockerfile 进行配置（敏感信息如密码，api-key等通过云托管部署参数进行配置）
- 开发环境通过环境变量进行配置（具体看项目的 README.md）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dish_memo
    username: root
    password: ${PASSWORD}
  data:
    redis:
      host: localhost
      port: 6379
      database: 0

server:
  port: 8080

dish-memo:
  suggestion:
    image-url-allowed-hosts: oss.example.com,img.example.com,7072-prod-d5gdc5h99b1442a27-1424479475.tcb.qcloud.la
    bailian:
      api-key: ${BAILIAN_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model: qwen3.6-flash
```

---

## 3. 错误码

| 业务码 | HTTP 状态码 | 含义 | 调用方处理建议 |
|---|---:|---|---|
| 0 | 200 | 成功 | 正常处理 |
| 4001001 | 400 | 参数错误 | 修正参数后重试 |
| 4011001 | 401 | 认证失败 | 重新登录 |
| 4031001 | 403 | 无权限访问该资源 | 检查用户身份 |
| 4041001 | 404 | 菜品记录不存在 | 检查记录 ID |
| 4091001 | 409 | 状态冲突 | 根据当前状态调整流程 |
| 4221001 | 422 | LLM 菜名生成失败 | 允许用户手动填写 |
| 5001001 | 500 | 服务内部异常 | 必要时重试 |
| 5001002 | 500 | 对象存储访问失败 | 必要时重试 |

错误响应示例：

```json
{
  "code": 4001001,
  "message": "meal_type is invalid",
  "data": null
}
```

---

## 4. 接口列表

| 编号 | 接口名称 | 方法 | 路径 |
|---|---|---|---|
| 4.1 | 基于图片生成菜名建议 | `POST` | `/dishes/name-suggestions` |
| 4.2 | 新增菜品记录 | `POST` | `/dishes` |
| 4.3 | 菜品列表查询 | `GET` | `/dishes` |
| 4.4 | 菜品详情查询 | `GET` | `/dishes/{dish_id}` |
| 4.5 | 编辑菜品记录 | `PUT` | `/dishes/{dish_id}` |
| 4.6 | 删除菜品记录 | `DELETE` | `/dishes/{dish_id}` |
| 4.7 | “今天吃什么”推荐 | `GET` | `/recommendations/today-meals` |

---

## 5. 详细接口定义

## 5.1 基于图片生成菜名建议

- **方法**：`POST`
- **路径**：`/dishes/name-suggestions`
- **说明**：根据前端已上传到对象存储的图片临时 URL 调用阿里云百炼 `qwen3.6-flash` 多模态模型，生成 1 个菜名建议；失败时前端应允许用户手动填写。

### 请求参数

- 参数格式——`application/json`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| image_url | string | 是 | 对象存储文件的临时 URL | 仅允许 `https`，域名必须命中服务端白名单 |

### 请求示例

```json
{
  "image_url": "https://oss.example.com/temp/dish_01.jpg"
}
```

### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| suggested_name | string/null | 建议菜名；生成失败时为 `null` |
| model_status | string | `success` / `failed` |
| reason | string/null | 失败原因摘要 |

### 成功响应示例

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

### 降级响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "suggested_name": null,
    "model_status": "failed",
    "reason": "model inference timeout"
  }
}
```

### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| image_url 为空 | 400 | 4001001 | 缺少图片文件 url |
| image_url 非法 | 400 | 4001001 | 图片文件 url 格式不合法 |
| image_url 不可访问或调用模型网络异常 | 500 | 5001002 | 后端无法通过对象存储读取图片或模型调用网络异常 |
| LLM 响应无法解析 | 422 | 4221001 | 模型响应不是接口要求的结构化内容 |

> 说明：LLM 生成失败不导致返回整体报错，通过 `model_status=failed` 降级，保持新增流程可继续。

---

## 5.2 新增菜品记录

- **方法**：`POST`
- **路径**：`/dishes`
- **说明**：创建一条菜品记录。名称、图片、日期、餐别为必填。

### 请求参数

- 参数格式——`application/json`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| name | string | 是 | 菜品名称 | 1~50 字符 |
| file_id | string | 是 | 菜品图片文件 ID | 单图，来自对象存储 |
| note | string | 否 | 备注 | 最大 500 字符 |
| date | string | 是 | 日期 | `2026-04-18` |
| meal_type | string | 是 | 餐别 | `breakfast/lunch/dinner` |

### 请求示例

```json
{
  "name": "番茄炒蛋",
  "file_id": "production/dish/u_1001/img_01HRXYZ.jpg",
  "note": "这次加了点糖，口感更平衡",
  "date": "2026-04-18",
  "meal_type": "dinner"
}
```

### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 菜品名称 |
| file_id | string | 图片文件 ID |
| note | string/null | 备注 |
| date | string | 日期 |
| meal_type | string | 餐别 |
| created_at | string | 创建时间 |

### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "dish_01JABCXYZ",
    "name": "番茄炒蛋",
    "file_id": "production/dish/u_1001/img_01HRXYZ.jpg",
    "note": "这次加了点糖，口感更平衡",
    "date": "2026-04-18",
    "meal_type": "dinner",
    "created_at": "2026-04-18T10:23:11+08:00"
  }
}
```

### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| 名称为空 | 400 | 4001001 | 菜品名称必填 |
| file_id 为空 | 400 | 4001001 | 图片文件 ID 必填 |
| 日期为空或格式错误 | 400 | 4001001 | 日期不合法 |
| 餐别非法 | 400 | 4001001 | meal_type 不在枚举范围内 |

---

## 5.3 菜品列表查询

- **方法**：`GET`
- **路径**：`/dishes`
- **说明**：分页查询当前用户的历史菜品记录，支持按餐别、日期区间、关键字筛选。

### 请求参数

- 参数格式——`query`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| page_no | integer | 否 | 页码 | 默认 `1` |
| page_size | integer | 否 | 每页数量 | 默认 `20`，最大 `100` |
| meal_type | string | 否 | 餐别筛选 | `breakfast/lunch/dinner` |
| date_from | string | 否 | 起始日期 | `2026-04-01` |
| date_to | string | 否 | 结束日期 | `2026-04-30` |
| keyword | string | 否 | 菜名关键字 | 最长 50 字符 |

### 请求示例

```http
GET /api/v1/dishes?page_no=1&page_size=20&meal_type=dinner&keyword=番茄
```

### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| list | array<object> | 记录列表 |
| total | integer | 总记录数 |
| page_no | integer | 当前页码 |
| page_size | integer | 每页数量 |

#### list 元素字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 菜品名称 |
| file_id | string | 图片文件 ID |
| date | string | 日期 |
| meal_type | string | 餐别 |
| updated_at | string | 更新时间 |

### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [
      {
        "id": "dish_01JABCXYZ",
        "name": "番茄炒蛋",
        "file_id": "production/dish/u_1001/img_01HRXYZ.jpg",
        "date": "2026-04-18",
        "meal_type": "dinner",
        "updated_at": "2026-04-18T10:23:11+08:00"
      }
    ],
    "total": 1,
    "page_no": 1,
    "page_size": 20
  }
}
```

### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| page_no 非法 | 400 | 4001001 | 页码必须大于 0 |
| page_size 非法 | 400 | 4001001 | 每页数量超限 |
| 日期区间非法 | 400 | 4001001 | `date_from` 大于 `date_to` |

---

## 5.4 菜品详情查询

- **方法**：`GET`
- **路径**：`/dishes/{dish_id}`
- **说明**：查询某条菜品记录完整信息。

### 路径参数

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| dish_id | string | 是 | 菜品记录 ID | `dish_01JABCXYZ` |

### 请求示例

```http
GET /api/v1/dishes/dish_01JABCXYZ
```

### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 菜品名称 |
| file_id | string | 图片文件 ID |
| note | string/null | 备注 |
| date | string | 日期 |
| meal_type | string | 餐别 |
| created_at | string | 创建时间 |
| updated_at | string | 更新时间 |

### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "dish_01JABCXYZ",
    "name": "番茄炒蛋",
    "file_id": "production/dish/u_1001/img_01HRXYZ.jpg",
    "note": "这次加了点糖，口感更平衡",
    "date": "2026-04-18",
    "meal_type": "dinner",
    "created_at": "2026-04-18T10:23:11+08:00",
    "updated_at": "2026-04-18T10:23:11+08:00"
  }
}
```

### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| dish_id 不存在 | 404 | 4041001 | 记录不存在 |
| 访问他人数据 | 403 | 4031001 | 无权限 |

---

## 5.5 编辑菜品记录

- **方法**：`PUT`
- **路径**：`/dishes/{dish_id}`
- **说明**：更新菜品信息。名称、图片、日期、餐别更新后立即对列表、详情、推荐生效。

### 路径参数

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| dish_id | string | 是 | 菜品记录 ID | `dish_01JABCXYZ` |

### 请求参数

- 参数格式——`application/json`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| name | string | 是 | 菜品名称 | 1~50 字符 |
| file_id | string | 是 | 菜品图片文件 ID | 单图，来自对象存储 |
| note | string | 否 | 备注 | 最大 500 字符 |
| date | string | 是 | 日期 | `2026-04-18` |
| meal_type | string | 是 | 餐别 | `breakfast/lunch/dinner` |

### 请求示例

```json
{
  "name": "家常番茄炒蛋",
  "file_id": "production/dish/u_1001/img_01HRXYZ_v2.jpg",
  "note": "改成了更嫩一点的做法",
  "date": "2026-04-18",
  "meal_type": "lunch"
}
```

### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 最新菜品名称 |
| file_id | string | 最新图片文件 ID |
| note | string/null | 最新备注 |
| date | string | 最新日期 |
| meal_type | string | 最新餐别 |
| updated_at | string | 更新时间 |

### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "dish_01JABCXYZ",
    "name": "家常番茄炒蛋",
    "file_id": "production/dish/u_1001/img_01HRXYZ_v2.jpg",
    "note": "改成了更嫩一点的做法",
    "date": "2026-04-18",
    "meal_type": "lunch",
    "updated_at": "2026-04-18T11:10:03+08:00"
  }
}
```

### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| dish_id 不存在 | 404 | 4041001 | 记录不存在 |
| 访问他人数据 | 403 | 4031001 | 无权限 |
| 必填字段缺失 | 400 | 4001001 | 不允许保存为空 |

> 说明：若前端替换图片并希望重新获得菜名建议，应先再次调用 **5.1 基于图片生成菜名建议**，再由用户确认是否覆盖原名称。

---

## 5.6 删除菜品记录

- **方法**：`DELETE`
- **路径**：`/dishes/{dish_id}`
- **说明**：删除指定菜品记录。

### 路径参数

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| dish_id | string | 是 | 菜品记录 ID | `dish_01JABCXYZ` |

### 请求示例

```http
DELETE /api/v1/dishes/dish_01JABCXYZ
```

### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| success | boolean | 是否删除成功 |

### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "success": true
  }
}
```

### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| dish_id 不存在 | 404 | 4041001 | 记录不存在 |
| 访问他人数据 | 403 | 4031001 | 无权限 |

---

## 5.7 “今天吃什么”推荐

- **方法**：`GET`
- **路径**：`/recommendations/today-meals`
- **说明**：根据用户选择的餐别，从该用户历史同餐别记录中随机抽取候选菜品。默认返回 3 个，不重复；不足则返回全部。

### 请求参数

- 参数格式——`query`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| meal_type | string | 是 | 餐别 | `breakfast/lunch/dinner` |
| size | integer | 否 | 候选数量 | 默认 `3`，最大 `10` |
| refresh_token | string | 否 | 换一批标识 | 前端可传随机串，便于追踪一次刷新 |

### 请求示例

```http
GET /api/v1/recommendations/today-meals?meal_type=breakfast&size=3&refresh_token=r_20260418_01
```

### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| meal_type | string | 餐别 |
| requested_size | integer | 请求数量 |
| actual_size | integer | 实际返回数量 |
| is_empty | boolean | 是否为空 |
| empty_tip | string/null | 空状态提示 |
| list | array<object> | 候选菜品列表 |

#### list 元素字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 菜品名称 |
| file_id | string | 图片文件 ID |
| date | string | 历史日期 |
| meal_type | string | 餐别 |

### 成功响应示例（有数据）

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "meal_type": "breakfast",
    "requested_size": 3,
    "actual_size": 3,
    "is_empty": false,
    "empty_tip": null,
    "list": [
      {
        "id": "dish_001",
        "name": "三明治",
        "file_id": "production/dish/u_1001/001.jpg",
        "date": "2026-04-02",
        "meal_type": "breakfast"
      },
      {
        "id": "dish_002",
        "name": "煎蛋吐司",
        "file_id": "production/dish/u_1001/002.jpg",
        "date": "2026-04-08",
        "meal_type": "breakfast"
      },
      {
        "id": "dish_003",
        "name": "牛奶麦片",
        "file_id": "production/dish/u_1001/003.jpg",
        "date": "2026-04-16",
        "meal_type": "breakfast"
      }
    ]
  }
}
```

### 成功响应示例（空状态）

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "meal_type": "breakfast",
    "requested_size": 3,
    "actual_size": 0,
    "is_empty": true,
    "empty_tip": "你还没有记录过这类餐食",
    "list": []
  }
}
```

### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| meal_type 缺失 | 400 | 4001001 | 必须指定餐别 |
| meal_type 非法 | 400 | 4001001 | 餐别不在枚举范围内 |
| size 非法 | 400 | 4001001 | 数量必须大于 0 |

---

## 7. 其他

- `X-Request-Id` 贯穿网关、服务、LLM 调用链路

---

## 8. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.1.0 | 2026-04-28 | 图片上传改为前端直传对象存储；后端仅存储并返回 `file_id`，不再提供图片上传接口或返回 `image_url` |
| v1.0.0 | 2026-04-18 | 根据 MVP PRD 产出首版接口文档与请求链路流程图 |
