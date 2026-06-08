# Locust 压测

压测统一使用 `perf/run_perf_suite.sh` 作为入口。该脚本负责数据准备、远程 SQL 导入、Locust 执行、报告生成和数据清理。

## 使用方式

```bash
HOST=http://47.94.9.240:8080 \
ECS_HOST=47.94.9.240 \
MYSQL_USER=root \
MYSQL_PASSWORD='***' \
bash perf/run_perf_suite.sh smoke get_dishes_list
```

参数：

- `phase`：`smoke`、`baseline` 或 `target`。
- `test`：单个 Locust 文件对应的测试，或 `suite`。

可选测试：

- `post_dish`
- `get_dishes_list`
- `get_dish_detail`
- `get_today_meals`
- `put_dish`
- `delete_dish`
- `mixed`
- `suite`
- `reachability`

`suite` 会按以下顺序运行所有 Locust 文件：

1. `post_dish`
2. `get_dishes_list`
3. `get_dish_detail`
4. `get_today_meals`
5. `put_dish`
6. `delete_dish`
7. `mixed`

## 数据准备

`perf/run_perf_suite.sh` 会在 Locust 启动前调用 `perf/generate_prepare_sql.py`。生成脚本会写出：

- `prepare.sql`：插入当前测试所需的 `dish_record` 预置数据。
- `cleanup.sql`：删除当前 run id 对应的数据。
- `dish_ids.txt`：按行记录菜品 ID，供 GET 详情、PUT、DELETE 和 mixed 测试消费。
- `dish_payloads.jsonl`：请求体数据，供 POST 和 PUT 测试消费。

默认情况下，GET、PUT、DELETE、推荐、mixed 和 suite 测试会准备 `30000` 行数据。仅运行 POST 测试时只生成请求 payload，不插入 dish 预置数据。

```bash
PREPARE_ROW_COUNT=30000 PAYLOAD_COUNT=1000 bash perf/run_perf_suite.sh baseline suite
```

## 远程 MySQL

runner 会通过 SSH 导入和清理数据：

```bash
ECS_HOST=47.94.9.240
ECS_USER=root
ECS_SSH_PORT=22
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD='***'
MYSQL_DATABASE=dish_memo
```

也可以传入 `ECS_SSH_TARGET=user@host` 来替代 `ECS_USER` 和 `ECS_HOST`。

## 输出文件

默认输出到 `perf/results/<run-id>/` 目录下：

- `<phase>/data/prepare.sql`
- `<phase>/data/cleanup.sql`
- `<phase>/data/dish_ids.txt`
- `<phase>/data/dish_payloads.jsonl`
- `<phase>/<test>/console.log`
- `<phase>/<test>/locust.log`
- `<phase>/<test>/stats*.csv`
- `<phase>/<test>/summary.csv`
- `<phase>/summary.md`
