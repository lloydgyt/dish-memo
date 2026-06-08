# Locust pressure tests

Use `perf/run_perf_suite.sh` as the entrypoint for pressure tests. The script owns data preparation, remote SQL import, Locust execution, report rendering, and cleanup.

## Usage

```bash
HOST=http://47.94.9.240:8080 \
ECS_HOST=47.94.9.240 \
MYSQL_USER=root \
MYSQL_PASSWORD='***' \
bash perf/run_perf_suite.sh smoke get_dishes_list
```

Arguments:

- `phase`: `smoke`, `baseline`, or `target`.
- `test`: one single Locust file or `suite`.

Available tests:

- `post_dish`
- `get_dishes_list`
- `get_dish_detail`
- `get_today_meals`
- `put_dish`
- `delete_dish`
- `mixed`
- `suite`
- `reachability`

`suite` runs every Locust file in this order:

1. `post_dish`
2. `get_dishes_list`
3. `get_dish_detail`
4. `get_today_meals`
5. `put_dish`
6. `delete_dish`
7. `mixed`

## Data Preparation

`perf/run_perf_suite.sh` calls `perf/generate_prepare_sql.py` before Locust starts. The generator writes:

- `prepare.sql`: inserts the prepared `dish_record` rows for the selected test.
- `cleanup.sql`: deletes rows for the current run id.
- `dish_ids.txt`: newline-separated IDs consumed by GET detail, PUT, DELETE, and mixed tests.
- `dish_payloads.jsonl`: request bodies consumed by POST and PUT tests.

By default, GET, PUT, DELETE, recommendation, mixed, and suite tests prepare `30000` rows. POST-only tests generate payloads but do not insert dish rows.

```bash
PREPARE_ROW_COUNT=30000 PAYLOAD_COUNT=1000 bash perf/run_perf_suite.sh baseline suite
```

## Remote MySQL

The runner imports and cleans data through SSH:

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

You can also pass `ECS_SSH_TARGET=user@host` instead of `ECS_USER` and `ECS_HOST`.

## Outputs

Outputs are written under `perf/results/<run-id>/` by default:

- `<phase>/data/prepare.sql`
- `<phase>/data/cleanup.sql`
- `<phase>/data/dish_ids.txt`
- `<phase>/data/dish_payloads.jsonl`
- `<phase>/<test>/console.log`
- `<phase>/<test>/locust.log`
- `<phase>/<test>/stats*.csv`
- `<phase>/<test>/summary.csv`
- `<phase>/summary.md`
