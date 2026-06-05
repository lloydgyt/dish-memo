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

Run reachability checks plus smoke, baseline, and target Locust stages:

```bash
bash perf/run_perf_suite.sh all
```

Run one stage only:

```bash
bash perf/run_perf_suite.sh reachability
bash perf/run_perf_suite.sh smoke
bash perf/run_perf_suite.sh baseline
bash perf/run_perf_suite.sh target
```

The suite uses `uvx locust` for Locust runs. Every Locust stage runs all single-endpoint scripts and the mixed behavior script.

Common options:

```bash
HOST=http://localhost:8000 \
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

Data seeding:

```bash
SEED_COUNT=20 DELETE_SEED_COUNT=1000 bash perf/run_perf_suite.sh baseline
```

`DELETE /dishes/{dish_id}` is destructive, so the suite creates a dedicated ID file for each delete test run. Increase `DELETE_SEED_COUNT` for long target tests.

Outputs are written under `perf/results/<timestamp>/` by default:

- `reachability/*.json`: curl response bodies.
- `reachability/*.status`: curl HTTP status codes.
- `<stage>/<script>/console.log`: Locust console output.
- `<stage>/<script>/locust.log`: Locust log file.
- `<stage>/<script>/stats*.csv`: Locust request statistics, failures, and exception CSV files.
- `<stage>/<script>/report.html`: Locust HTML report.
