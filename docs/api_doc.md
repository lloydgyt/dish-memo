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

#### DishRecord

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID（主键） |
| user_id | string | 用户 ID |
| name | string | 菜品名称 |
| file_id | string | 菜品图片在对象存储中的文件 ID |
| note | string/null | 备注 |
| date | string | 做菜/食用日期，格式 `YYYY-MM-DD` |
| meal_type | string | 餐别，取值 breakfast, lunch, dinner |
| created_at | string | 创建时间，RFC3339 |
| updated_at | string | 更新时间，RFC3339 |

#### User

| 字段名 | 类型 | 说明 |
|---|---|---|
| uid | string | 用户主键，唯一标识一个业务用户 |
| nickname | string | 用户昵称 |
| avatar_url | string/null | 用户头像地址 |
| created_at | string | 创建时间，RFC3339 |
| updated_at | string | 更新时间，RFC3339 |

#### FriendRelation

| 字段名 | 类型 | 说明 |
|---|---|---|
| uid_a | string | 好友关系中排序后较小的一侧 uid |
| uid_b | string | 好友关系中排序后较大的一侧 uid |
| created_at | string | 建立好友关系时间，RFC3339 |

> 索引约束：建立 `(uid_a, uid_b)` 联合唯一索引。写入前服务端需先将两个 uid 归一化排序，避免同一好友关系出现双向重复记录。

#### FriendInvitation

| 字段名 | 类型 | 说明 |
|---|---|---|
| inviter_uid | string | 已发送好友请求的用户 uid |
| expire_at | string | 邀请过期时间，RFC3339 |
| created_at | string | 创建时间，RFC3339 |

> 索引约束：建议对 `inviter_uid` 建索引。服务端确认添加好友前需先解析 `inviteToken` 得到 A 的 uid，再检查 `FriendInvitation` 是否存在 A 的有效好友请求记录。

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

### 2.7  日志格式

#### summary log

- 所有的 request 都对应一个 summary log

JSON 格式

|     字段名     |                             说明                             |
| :------------: | :----------------------------------------------------------: |
|   request_id   |                   记录同一个请求链路的 id                    |
|    user_id     |                   用户 ID - 实质是 openid                    |
| request_params |                         请求时的参数                         |
|     route      |                     请求方法 与 请求路径                     |
|     status     |                       该请求的响应状态                       |
|  duration_ms   | 请求持续时间，以 ms 为单位。记录的是从进入 Filter 开始到退出 Filter 前之间的总时间 |
| db_duration_ms |        记录的是进入 Mapper 到退出 Mapper 之间的总时间        |

示例

``` json
{
    "request_id":"req_1778054141288_lsh1v551",
    "user_id":"o3kVk3YbeZT5cfD51vikUZM1LcOU",
    "request_params":{
            "page_no":"1",
        	"page_size":"20",
        	"meal_type":"lunch"
    },
	"route": "GET /api/v1/dishes",
    "status":200,
    "duration_ms":8,
	"db_duration_ms": 31
}
```

#### 详细阶段 log

1. 详细阶段 log 仅当 request 的 duration_ms 大于阈值（默认 500ms）的时候记录

JSON 格式

|         字段名         |                     说明                     |
| :--------------------: | :------------------------------------------: |
|       request_id       |           记录同一个请求链路的 id            |
|     controller_ms      |              controller 层耗时               |
|       service_ms       |               service 层的耗时               |
|         mapper         | 一个数组，调用 n 个mapper，便有 n 个数组元素 |
|   mapper.duration_ms   |               mapper 层的耗时                |
|  mapper.statement_id   |              指明哪一个 Mapper               |
|    mapper.db_table     |   数据库名及表名（格式如“数据库名:表名”）    |
|   mapper.result_size   |         mapper层最终映射后的对象数量         |
| mapper.sql_fingerprint |     SQL fingerprint 需要去掉 trace 注释      |

示例

```json
{
    "request_id":"req_1778054141288_lsh1v551",
	"controller_ms": 20,
    "service_ms": 30,
    "mapper": [
		{
             "duration_ms": 500,
             "statement_id": "com.example.mapper.OrderMapper.selectById",
             "db_table": "dish_memo:dish_record",
             "result_size": 20,
			"sql_fingerprint": "SELECT * FROM orders WHERE id = ?"    
		},
    ]
}
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
| 4002001 | 400 | uid 缺失或非法 | 检查登录态与请求参数 |
| 4002002 | 400 | 不允许添加自己为好友 | 终止当前操作 |
| 4042001 | 404 | 好友请求不存在 | 检查 inviteToken 或重新发起好友请求 |
| 4092001 | 409 | 好友关系已存在 | 按已添加状态展示 |
| 4092002 | 409 | 好友请求状态冲突 | 刷新页面并重新确认状态 |
| 4102001 | 410 | inviteToken 已过期 | 重新生成 inviteToken |
| 4222001 | 422 | inviteToken 签名非法或内容被篡改 | 拒绝处理并提示重新打开有效 token |
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
| 4.8 | 接受好友 | `POST` | `/friends/invitations` |
| 4.9 | 解析并校验 inviteToken | `POST` | `/friends/invitations/parse` |
| 4.10 | 查询邀请是否可用 | `GET` | `/friends/invitations/{inviteToken}` |
| 4.11 | 确认添加好友 | `POST` | `/friends` |
| 4.12 | 查询好友列表 | `GET` | `/friends` |

---

## 5. 详细接口定义

### 5.1 菜品模块

#### 5.1.1 基于图片生成菜名建议

- **方法**：`POST`
- **路径**：`/dishes/name-suggestions`
- **说明**：根据前端已上传到对象存储的图片临时 URL 调用阿里云百炼 `qwen3.6-flash` 多模态模型，生成 1 个菜名建议；失败时前端应允许用户手动填写。

##### 请求参数

- 参数格式——`application/json`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| image_url | string | 是 | 对象存储文件的临时 URL | 仅允许 `https`，域名必须命中服务端白名单 |

##### 请求示例

```json
{
  "image_url": "https://7072-prod-d5gdc5h99b1442a27-1424479475.tcb.qcloud.la/production/dish/local-user/img_1777367830994_ci4nbo3l.jpg?sign=79eda24322761d1500ea8247f5a4afc2&t=1778555502"
}
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| suggested_name | string/null | 建议菜名；生成失败时为 `null` |
| model_status | string | `success` / `failed` |
| reason | string/null | 失败原因摘要 |

##### 成功响应示例

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

##### 降级响应示例

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

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| image_url 为空 | 400 | 4001001 | 缺少图片文件 url |
| image_url 非法 | 400 | 4001001 | 图片文件 url 格式不合法 |
| image_url 不可访问或调用模型网络异常 | 500 | 5001002 | 后端无法通过对象存储读取图片或模型调用网络异常 |
| LLM 响应无法解析 | 422 | 4221001 | 模型响应不是接口要求的结构化内容 |

> 说明：LLM 生成失败不导致返回整体报错，通过 `model_status=failed` 降级，保持新增流程可继续。

---

#### 5.1.2 新增菜品记录

- **方法**：`POST`
- **路径**：`/dishes`
- **说明**：创建一条菜品记录。名称、图片、日期、餐别为必填。

##### 请求参数

- 参数格式——`application/json`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| name | string | 是 | 菜品名称 | 1~50 字符 |
| file_id | string | 是 | 菜品图片文件 ID | 单图，来自对象存储 |
| note | string | 否 | 备注 | 最大 500 字符 |
| date | string | 是 | 日期 | `2026-04-18` |
| meal_type | string | 是 | 餐别 | `breakfast/lunch/dinner` |

##### 请求示例

```json
{
  "name": "番茄炒蛋",
  "file_id": "production/dish/u_1001/img_01HRXYZ.jpg",
  "note": "这次加了点糖，口感更平衡",
  "date": "2026-04-18",
  "meal_type": "dinner"
}
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 菜品名称 |
| file_id | string | 图片文件 ID |
| note | string/null | 备注 |
| date | string | 日期 |
| meal_type | string | 餐别 |
| created_at | string | 创建时间 |

##### 成功响应示例

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

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| 名称为空 | 400 | 4001001 | 菜品名称必填 |
| file_id 为空 | 400 | 4001001 | 图片文件 ID 必填 |
| 日期为空或格式错误 | 400 | 4001001 | 日期不合法 |
| 餐别非法 | 400 | 4001001 | meal_type 不在枚举范围内 |

---

#### 5.1.3 菜品列表查询

- **方法**：`GET`
- **路径**：`/dishes`
- **说明**：分页查询当前用户的历史菜品记录，支持按餐别、日期区间、关键字筛选。

##### 请求参数

- 参数格式——`query`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| page_no | integer | 否 | 页码 | 默认 `1` |
| page_size | integer | 否 | 每页数量 | 默认 `20`，最大 `100` |
| meal_type | string | 否 | 餐别筛选 | `breakfast/lunch/dinner` |
| date_from | string | 否 | 起始日期 | `2026-04-01` |
| date_to | string | 否 | 结束日期 | `2026-04-30` |
| keyword | string | 否 | 菜名关键字 | 最长 50 字符 |

##### 请求示例

```http
GET /api/v1/dishes?page_no=1&page_size=20&meal_type=dinner&keyword=番茄
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| list | array<object> | 记录列表 |
| total | integer | 总记录数 |
| page_no | integer | 当前页码 |
| page_size | integer | 每页数量 |

###### list 元素字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 菜品名称 |
| file_id | string | 图片文件 ID |
| date | string | 日期 |
| meal_type | string | 餐别 |
| updated_at | string | 更新时间 |

##### 成功响应示例

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

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| page_no 非法 | 400 | 4001001 | 页码必须大于 0 |
| page_size 非法 | 400 | 4001001 | 每页数量超限 |
| 日期区间非法 | 400 | 4001001 | `date_from` 大于 `date_to` |

---

#### 5.1.4 菜品详情查询

- **方法**：`GET`
- **路径**：`/dishes/{dish_id}`
- **说明**：查询某条菜品记录完整信息。

##### 路径参数

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| dish_id | string | 是 | 菜品记录 ID | `dish_01JABCXYZ` |

##### 请求示例

```http
GET /api/v1/dishes/dish_01JABCXYZ
```

##### 响应参数

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

##### 成功响应示例

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

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| dish_id 不存在 | 404 | 4041001 | 记录不存在 |
| 访问他人数据 | 403 | 4031001 | 无权限 |

---

#### 5.1.5 编辑菜品记录

- **方法**：`PUT`
- **路径**：`/dishes/{dish_id}`
- **说明**：更新菜品信息。名称、图片、日期、餐别更新后立即对列表、详情、推荐生效。

##### 路径参数

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| dish_id | string | 是 | 菜品记录 ID | `dish_01JABCXYZ` |

##### 请求参数

- 参数格式——`application/json`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| name | string | 是 | 菜品名称 | 1~50 字符 |
| file_id | string | 是 | 菜品图片文件 ID | 单图，来自对象存储 |
| note | string | 否 | 备注 | 最大 500 字符 |
| date | string | 是 | 日期 | `2026-04-18` |
| meal_type | string | 是 | 餐别 | `breakfast/lunch/dinner` |

##### 请求示例

```json
{
  "name": "家常番茄炒蛋",
  "file_id": "production/dish/u_1001/img_01HRXYZ_v2.jpg",
  "note": "改成了更嫩一点的做法",
  "date": "2026-04-18",
  "meal_type": "lunch"
}
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 最新菜品名称 |
| file_id | string | 最新图片文件 ID |
| note | string/null | 最新备注 |
| date | string | 最新日期 |
| meal_type | string | 最新餐别 |
| updated_at | string | 更新时间 |

##### 成功响应示例

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

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| dish_id 不存在 | 404 | 4041001 | 记录不存在 |
| 访问他人数据 | 403 | 4031001 | 无权限 |
| 必填字段缺失 | 400 | 4001001 | 不允许保存为空 |

> 说明：若前端替换图片并希望重新获得菜名建议，应先再次调用 **5.1 基于图片生成菜名建议**，再由用户确认是否覆盖原名称。

---

#### 5.1.6 删除菜品记录

- **方法**：`DELETE`
- **路径**：`/dishes/{dish_id}`
- **说明**：删除指定菜品记录。

##### 路径参数

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| dish_id | string | 是 | 菜品记录 ID | `dish_01JABCXYZ` |

##### 请求示例

```http
DELETE /api/v1/dishes/dish_01JABCXYZ
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| success | boolean | 是否删除成功 |

##### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "success": true
  }
}
```

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| dish_id 不存在 | 404 | 4041001 | 记录不存在 |
| 访问他人数据 | 403 | 4031001 | 无权限 |

---

#### 5.1.7 “今天吃什么”推荐

- **方法**：`GET`
- **路径**：`/recommendations/today-meals`
- **说明**：根据用户选择的餐别，从该用户历史同餐别记录中随机抽取候选菜品。默认返回 3 个，不重复；不足则返回全部。

##### 请求参数

- 参数格式——`query`

| 参数名 | 类型 | 必填 | 说明 | 约束/示例 |
|---|---|---:|---|---|
| meal_type | string | 是 | 餐别 | `breakfast/lunch/dinner` |
| size | integer | 否 | 候选数量 | 默认 `3`，最大 `10` |
| refresh_token | string | 否 | 换一批标识 | 前端可传随机串，便于追踪一次刷新 |

##### 请求示例

```http
GET /api/v1/recommendations/today-meals?meal_type=breakfast&size=3&refresh_token=r_20260418_01
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| meal_type | string | 餐别 |
| requested_size | integer | 请求数量 |
| actual_size | integer | 实际返回数量 |
| is_empty | boolean | 是否为空 |
| empty_tip | string/null | 空状态提示 |
| list | array<object> | 候选菜品列表 |

###### list 元素字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | string | 菜品记录 ID |
| name | string | 菜品名称 |
| file_id | string | 图片文件 ID |
| date | string | 历史日期 |
| meal_type | string | 餐别 |

##### 成功响应示例（有数据）

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

##### 成功响应示例（空状态）

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

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| meal_type 缺失 | 400 | 4001001 | 必须指定餐别 |
| meal_type 非法 | 400 | 4001001 | 餐别不在枚举范围内 |
| size 非法 | 400 | 4001001 | 数量必须大于 0 |

---

### 5.2 好友模块

#### 5.2.1 邀请添加好友

- **方法**：`POST`
- **路径**：`/friends/invitations`
- **鉴权 Header**：`X-WX-OPENID` 必填，服务端据此解析当前登录 uid
- **说明**：A 邀请添加好友后，服务端将 A 的 uid 写入 `FriendInvitation`，并生成包含 A 的 uid、nickname 以及 avatar_url（这些信息服务端查询用户表可得到）、具备签名防篡改能力的 `inviteToken`。该记录表示 A 确实发送过好友请求，供 B 后续确认添加好友时校验。

##### 请求参数

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| expire_in_seconds | integer | 否 | 邀请有效期，默认 `86400`，服务端可配置上限 |

##### 请求示例

```json
{
  "expire_in_seconds": 86400
}
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| inviteToken | string | 包含 A uid 信息的好友邀请 token |
| expire_at | string | 过期时间，RFC3339 |

##### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "inviteToken": "fit_eyJ1aWQiOiJ1XzEwMDEiLCJleHAiOjE3Nzg3MzM2MDB9.ab12cd34",
    "expire_at": "2026-05-14T10:00:00+08:00"
  }
}
```

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| 当前登录 uid 为空或非法 | 400 | 4002001 | 拒绝生成 inviteToken |
| expire_in_seconds 非法 | 400 | 4001001 | 有效期必须为正整数并满足服务端限制 |

---

#### 5.2.2 解析 inviteToken

- **方法**：`POST`
- **路径**：`/friends/invitations/parse`
- **鉴权 Header**：`X-WX-OPENID` 必填
- **说明**：解析前端收到的 `inviteToken`，校验签名有效性、是否过期，并返回 A 的 nickname 和 avatar_url。该接口只做校验与解析，不建立好友关系。用于了解是谁发起的好友请求

##### 请求参数

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| inviteToken | string | 是 | A 发送给 B 的好友邀请 token |

##### 请求示例

```json
{
  "inviteToken": "fit_eyJ1aWQiOiJ1XzEwMDEiLCJleHAiOjE3Nzg3MzM2MDB9.ab12cd34"
}
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| nickname | string | inviteToken 中包含的邀请者的昵称 |
| avatar_url | string | inviteToken 中包含的邀请者的头像 url |

##### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
	"nickname" : "YT",
    "avatar_url": "https://oss.example.com/random1434"
  }
}
```

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| 当前登录 uid 为空或非法 | 400 | 4002001 | 拒绝解析 |
| inviteToken 缺失或格式非法 | 400 | 4001001 | 无法提取必要参数 |
| inviteToken 签名非法或内容被篡改 | 422 | 4222001 | token 不可信 |

---

#### 5.2.3 确认添加好友

- **方法**：`POST`
- **路径**：`/friends`
- **鉴权 Header**：`X-WX-OPENID` 必填
- **说明**：B 确认“添加 A 为好友”。服务端先解析 `inviteToken` 得到 A 的 uid，再检查 `FriendInvitation` 中是否存在 A 的有效好友请求记录，确认 A 确实发送过好友请求；校验通过后在同一事务内将 A/B uid 写入 `FriendRelation`。

##### 请求参数

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| inviteToken | string | 是 | A 发送给 B 的好友邀请 token |

##### 请求示例

```json
{
  "inviteToken": "fit_eyJ1aWQiOiJ1XzEwMDEiLCJleHAiOjE3Nzg3MzM2MDB9.ab12cd34"
}
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| inviter_uid | string | 发起好友邀请方（A）的 uid |
| friend_uid | string | 当前接收方（B）的 uid |

##### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "inviter_uid": "u_1001",
    "friend_uid": "u_2002",
  }
}
```

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| 当前登录 uid 为空或非法 | 400 | 4002001 | 拒绝处理 |
| inviteToken 缺失 | 400 | 4001001 | 必须提供 inviteToken |
| 好友请求不存在 | 404 | 4042001 | `FriendInvitation` 中不存在 A 的有效好友请求记录 |
| 接收方与邀请发起方相同 | 400 | 4002002 | 不允许自添加 |
| 好友关系已存在且非同一次幂等重试 | 409 | 4092001 | 不重复创建关系 |
| 邀请已过期 | 410 | 4102001 | 不允许继续使用 |
| inviteToken 签名非法或内容被篡改 | 422 | 4222001 | token 不可信 |

---

#### 5.2.4 查询好友列表

- **方法**：`GET`
- **路径**：`/friends`
- **鉴权 Header**：`X-WX-OPENID` 必填
- **说明**：按当前登录 uid 关联查询好友关系表，并补充用户基础信息。支持分页和 nickname 过滤。

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| page_no | integer | 否 | 页码，默认 `1` |
| page_size | integer | 否 | 每页数量，默认 `20` |
| nickname_keyword | string | 否 | 按好友昵称模糊过滤 |

##### 请求示例

```text
GET /api/v1/friends?page_no=1&page_size=20&nickname_keyword=阿青
```

##### 响应参数

| 字段名 | 类型 | 说明 |
|---|---|---|
| page_no | integer | 当前页码 |
| page_size | integer | 当前分页大小 |
| total | integer | 总条数 |
| list | array | 好友列表 |

###### list 元素字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| uid | string | 好友 uid |
| nickname | string | 好友昵称 |
| avatar_url | string/null | 好友头像地址 |
| created_at | string | 建立好友关系时间，RFC3339 |

##### 成功响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "page_no": 1,
    "page_size": 20,
    "total": 1,
    "list": [
      {
        "uid": "u_1001",
        "nickname": "阿青",
        "avatar_url": null,
        "created_at": "2026-05-13T10:10:00+08:00"
      }
    ]
  }
}
```

##### 失败场景

| 场景 | HTTP 状态码 | 业务码 | 说明 |
|---|---:|---:|---|
| 当前登录 uid 为空或非法 | 400 | 4002001 | 拒绝查询 |
| page_no 或 page_size 非法 | 400 | 4001001 | 分页参数需满足正整数约束 |

---

## 6. 好友模块统一业务规则

- 所有好友模块接口均要求登录，并以 `X-WX-OPENID` 解析出的当前 uid 作为可信身份来源。
- 接受好友时，服务端需将 A 的 `uid`、`expire_at` 等字段纳入 `inviteToken` 签名校验范围，并在 `FriendInvitation` 中保留 A 的有效好友请求记录。
- 查询邀请可用性时，服务端需拒绝 uid 为空、接收方 uid 非法、自添加、好友关系已存在、好友请求不存在、`inviteToken` 过期等情况。
- 确认添加好友时，应先解析 `inviteToken` 得到 A 的 uid，再检查 `FriendInvitation` 是否存在 A 的有效好友请求记录；通过后在单一数据库事务中插入好友关系记录。
- 对 `inviteToken` 伪造、过期等异常，统一降级为标准业务码返回，不暴露内部签名细节或存储实现。

---

## 7. 其他

- `X-Request-Id` 贯穿网关、服务、LLM 调用链路

---

## 8. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.2.0 | 2026-05-13 | 新增好友邀请、邀请解析、好友确认、好友列表接口定义，并补充用户表、好友关系表、邀请表及相关错误码与边界规则 |
| v1.1.0 | 2026-04-28 | 图片上传改为前端直传对象存储；后端仅存储并返回 `file_id`，不再提供图片上传接口或返回 `image_url` |
| v1.0.0 | 2026-04-18 | 根据 MVP PRD 产出首版接口文档与请求链路流程图 |
