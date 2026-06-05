# Locust pressure tests

These scripts target the `/api/v1` dish APIs and use payloads compatible with `docs/table_ddl/perf_schema.sql`.

## Common environment variables

- `LOCUST_USER_ID`: defaults to `perf_user_001`.
- `LOCUST_API_PREFIX`: defaults to `/api/v1`.
- `LOCUST_DISH_IDS`: comma-separated existing dish IDs for detail/update/delete single-endpoint tests.
- `LOCUST_DISH_ID_FILE`: newline-separated existing dish IDs for detail/update/delete single-endpoint tests.
- `LOCUST_MEAL_TYPE`: optional fixed `breakfast`, `lunch`, or `dinner`.
- `LOCUST_PAGE_NO`, `LOCUST_PAGE_SIZE`, `LOCUST_KEYWORD`, `LOCUST_DATE_FROM`, `LOCUST_DATE_TO`: optional list query filters.
- `LOCUST_RECOMMENDATION_SIZE`: defaults to `3`.

## Single-endpoint scripts

```bash
locust -f perf/locust_get_dishes_list.py --host http://localhost:8080
locust -f perf/locust_get_dish_detail.py --host http://localhost:8080
locust -f perf/locust_post_dish.py --host http://localhost:8080
locust -f perf/locust_put_dish.py --host http://localhost:8080
locust -f perf/locust_delete_dish.py --host http://localhost:8080
locust -f perf/locust_get_today_meals.py --host http://localhost:8080
```

`GET /dishes/{dish_id}`, `PUT /dishes/{dish_id}`, and `DELETE /dishes/{dish_id}` need existing records owned by `LOCUST_USER_ID`. For delete tests, provide enough unique IDs because each ID is consumed once.

## Mixed behavior script

```bash
locust -f perf/locust_mixed_dish_behaviors.py --host http://localhost:8080
```

The mixed script creates its own records and then performs list, detail, update, delete, and recommendation requests as one user workflow.

## Automated staged suite

The recommended flow is:

```bash
HOST=http://localhost:8080 bash perf/run_perf_suite.sh reachability
HOST=http://localhost:8080 bash perf/run_perf_suite.sh smoke
HOST=http://localhost:8080 bash perf/run_perf_suite.sh baseline
```

Start `target` only after reachability, smoke, and baseline results look healthy:

```bash
HOST=http://localhost:8080 bash perf/run_perf_suite.sh target
```

The suite uses `uvx locust` for Locust runs. Every Locust stage runs all single-endpoint scripts and the mixed behavior script in this fixed order:

1. `POST /dishes`
2. `GET /dishes`
3. `GET /dishes/{dish_id}`
4. `GET /recommendations/today-meals`
5. `PUT /dishes/{dish_id}`
6. `DELETE /dishes/{dish_id}`
7. Mixed behavior

Common options:

```bash
HOST=http://localhost:8080 \
LOCUST_USER_ID=perf_user_001 \
RESULT_DIR=perf/results/manual_run \
bash perf/run_perf_suite.sh smoke
```

Stage defaults:

```bash
SMOKE_USERS=1 SMOKE_SPAWN_RATE=1 SMOKE_RUN_TIME=30s
BASELINE_USERS=5 BASELINE_SPAWN_RATE=1 BASELINE_RUN_TIME=2m
TARGET_USERS=20 TARGET_SPAWN_RATE=5 TARGET_RUN_TIME=5m
```

Dataset behavior:

```bash
DATASET_COUNT=1000 bash perf/run_perf_suite.sh baseline
```

For each Locust stage, `perf/generate_dataset.py` creates a deterministic JSONL payload dataset. `locust_post_dish.py` reads from that dataset and writes the created IDs to `created_dish_ids.txt`. Detail, PUT, and DELETE single-endpoint tests then use that intermediate ID file. The mixed behavior script manages its own created ID pool, so its PUT and DELETE operations only touch records that already exist in that workflow.

Outputs are written under `perf/results/<timestamp>/` by default:

- `reachability`: no output files; stdout prints `REACHABILITY OK` or `REACHABILITY FAILED: ...`.
- `<stage>/dish_payloads.jsonl`: deterministic payload dataset used by POST/PUT.
- `<stage>/created_dish_ids.txt`: IDs created by the POST single-endpoint test and reused by detail/PUT/DELETE.
- `<stage>/<script>/console.log`: Locust console output.
- `<stage>/<script>/locust.log`: Locust log file.
- `<stage>/<script>/stats*.csv`: Locust request statistics, failures, and exception CSV files.
- `<stage>/summary.csv` and `<stage>/summary.txt`: consolidated request count, failure count, QPS, average latency, and percentile latency.
- `<stage>/report.md`: Markdown summary report with test setup, key metrics, and user-filled Bottlenecks/Conclusion sections.
