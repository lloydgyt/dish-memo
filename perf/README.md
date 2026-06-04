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
