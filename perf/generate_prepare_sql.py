#!/usr/bin/env python3
import argparse
import json
from datetime import date, datetime, timedelta
from pathlib import Path


DISH_NAMES = (
    "番茄炒蛋",
    "青椒肉丝",
    "香煎鸡胸",
    "三明治",
    "牛奶麦片",
    "清炒西兰花",
    "红烧排骨",
    "虾仁炒饭",
)
MEAL_TYPES = ("breakfast", "lunch", "dinner")
DATA_TESTS = {
    "get_dishes_list",
    "get_dish_detail",
    "get_today_meals",
    "put_dish",
    "delete_dish",
    "mixed",
    "suite",
    "all",
}


def sql_string(value):
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def needs_rows(targets):
    return any(target in DATA_TESTS for target in targets)


def dish_id(run_id, index):
    return f"perf_{run_id}_{index:06d}"


def file_id_prefix(run_id, user_id):
    return f"production/dish/{user_id}/perf_{run_id}_"


def cleanup_condition(run_id, user_id):
    return (
        f"id LIKE {sql_string('perf_' + run_id + '_%')} "
        f"OR file_id LIKE {sql_string(file_id_prefix(run_id, user_id) + '%')}"
    )


def payload(run_id, index, user_id):
    meal_type = MEAL_TYPES[index % len(MEAL_TYPES)]
    dish_day = date.today() - timedelta(days=index % 90)
    suffix = f"{index:06d}"
    return {
        "id": dish_id(run_id, index),
        "user_id": user_id,
        "name": f"{DISH_NAMES[index % len(DISH_NAMES)]}-{suffix}",
        "file_id": f"{file_id_prefix(run_id, user_id)}{suffix}.jpg",
        "note": f"perf prepared item {run_id} {suffix}",
        "date": dish_day.isoformat(),
        "meal_type": meal_type,
        "created_at": datetime.combine(dish_day, datetime.min.time()).replace(hour=index % 24).strftime("%Y-%m-%d %H:%M:%S"),
        "updated_at": datetime.combine(dish_day, datetime.min.time()).replace(hour=(index + 1) % 24).strftime("%Y-%m-%d %H:%M:%S"),
    }


def write_payloads(output, run_id, user_id, count):
    with output.open("w", encoding="utf-8") as handle:
        for index in range(count):
            item = payload(run_id, index, user_id)
            request_payload = {
                "name": item["name"],
                "file_id": item["file_id"],
                "note": item["note"],
                "date": item["date"],
                "meal_type": item["meal_type"],
            }
            handle.write(json.dumps(request_payload, ensure_ascii=False))
            handle.write("\n")


def write_ids(output, run_id, count):
    with output.open("w", encoding="utf-8") as handle:
        for index in range(count):
            handle.write(dish_id(run_id, index))
            handle.write("\n")


def insert_statement(rows):
    values = []
    for row in rows:
        values.append(
            "("
            + ", ".join(
                sql_string(row[column])
                for column in (
                    "id",
                    "user_id",
                    "name",
                    "file_id",
                    "note",
                    "date",
                    "meal_type",
                    "created_at",
                    "updated_at",
                )
            )
            + ")"
        )
    return (
        "INSERT INTO dish_record "
        "(id, user_id, name, file_id, note, date, meal_type, created_at, updated_at)\nVALUES\n"
        + ",\n".join(values)
        + ";\n"
    )


def write_prepare_sql(output, run_id, user_id, count, batch_size, enabled):
    with output.open("w", encoding="utf-8") as handle:
        handle.write("SET NAMES utf8mb4;\n")
        handle.write("START TRANSACTION;\n")
        handle.write(
            "INSERT INTO `user` (uid, nickname, avatar_file_id, created_at, updated_at) "
            f"VALUES ({sql_string(user_id)}, {sql_string('perf user')}, NULL, NOW(), NOW()) "
            "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), updated_at = NOW();\n"
        )
        handle.write(f"DELETE FROM dish_record WHERE {cleanup_condition(run_id, user_id)};\n")
        if enabled:
            for start in range(0, count, batch_size):
                rows = [payload(run_id, index, user_id) for index in range(start, min(start + batch_size, count))]
                handle.write(insert_statement(rows))
        handle.write("COMMIT;\n")


def write_cleanup_sql(output, run_id, user_id):
    output.write_text(
        "SET NAMES utf8mb4;\n"
        "START TRANSACTION;\n"
        f"DELETE FROM dish_record WHERE {cleanup_condition(run_id, user_id)};\n"
        "COMMIT;\n",
        encoding="utf-8",
    )


def main():
    parser = argparse.ArgumentParser(description="Generate SQL and data-pool files for Locust API tests.")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--user-id", default="perf_user_001")
    parser.add_argument("--targets", required=True, help="Comma-separated run targets, for example get_dish_detail,suite")
    parser.add_argument("--row-count", type=int, default=30000)
    parser.add_argument("--payload-count", type=int, default=1000)
    parser.add_argument("--batch-size", type=int, default=500)
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    targets = [target.strip() for target in args.targets.split(",") if target.strip()]
    enabled = needs_rows(targets)

    prepare_sql = output_dir / "prepare.sql"
    cleanup_sql = output_dir / "cleanup.sql"
    dish_ids = output_dir / "dish_ids.txt"
    payloads = output_dir / "dish_payloads.jsonl"

    write_prepare_sql(prepare_sql, args.run_id, args.user_id, args.row_count, args.batch_size, enabled)
    write_cleanup_sql(cleanup_sql, args.run_id, args.user_id)
    write_ids(dish_ids, args.run_id, args.row_count if enabled else 0)
    write_payloads(payloads, args.run_id, args.user_id, args.payload_count)

    print(f"prepare_sql={prepare_sql}")
    print(f"cleanup_sql={cleanup_sql}")
    print(f"dish_ids={dish_ids}")
    print(f"payloads={payloads}")
    print(f"prepared_rows={args.row_count if enabled else 0}")


if __name__ == "__main__":
    main()
