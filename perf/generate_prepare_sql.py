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


def user_ids(user_id_prefix, count):
    if count <= 1:
        return [user_id_prefix]
    return [f"{user_id_prefix}_{index:06d}" for index in range(1, count + 1)]


def dish_id(run_id, user_index, row_index):
    return f"perf_{run_id}_{user_index:06d}_{row_index:06d}"


def file_id_prefix(run_id, user_id):
    return f"production/dish/{user_id}/perf_{run_id}_"


def cleanup_condition(run_id):
    return (
        f"id LIKE {sql_string('perf_' + run_id + '_%')} "
        f"OR file_id LIKE {sql_string('production/dish/%/perf_' + run_id + '_%')}"
    )


def payload(run_id, user_index, row_index, user_id):
    absolute_index = user_index * 1000000 + row_index
    meal_type = MEAL_TYPES[absolute_index % len(MEAL_TYPES)]
    dish_day = date.today() - timedelta(days=absolute_index % 90)
    suffix = f"{user_index:06d}_{row_index:06d}"
    return {
        "id": dish_id(run_id, user_index, row_index),
        "user_id": user_id,
        "name": f"{DISH_NAMES[absolute_index % len(DISH_NAMES)]}-{suffix}",
        "file_id": f"{file_id_prefix(run_id, user_id)}{suffix}.jpg",
        "note": f"perf prepared item {run_id} {suffix}",
        "date": dish_day.isoformat(),
        "meal_type": meal_type,
        "created_at": datetime.combine(dish_day, datetime.min.time()).replace(hour=absolute_index % 24).strftime("%Y-%m-%d %H:%M:%S"),
        "updated_at": datetime.combine(dish_day, datetime.min.time()).replace(hour=(absolute_index + 1) % 24).strftime("%Y-%m-%d %H:%M:%S"),
    }


def write_users(output, users):
    with output.open("w", encoding="utf-8") as handle:
        for user_id in users:
            handle.write(user_id)
            handle.write("\n")


def write_payloads(output, run_id, users, count):
    with output.open("w", encoding="utf-8") as handle:
        for index in range(count):
            user_index = index % len(users)
            row_index = index // len(users)
            user_id = users[user_index]
            item = payload(run_id, user_index, row_index, user_id)
            request_payload = {
                "_user_id": user_id,
                "name": item["name"],
                "file_id": item["file_id"],
                "note": item["note"],
                "date": item["date"],
                "meal_type": item["meal_type"],
            }
            handle.write(json.dumps(request_payload, ensure_ascii=False))
            handle.write("\n")


def write_ids(output, run_id, users, rows_per_user, enabled):
    with output.open("w", encoding="utf-8") as handle:
        if not enabled:
            return
        for user_index, user_id in enumerate(users):
            for row_index in range(rows_per_user):
                handle.write(user_id)
                handle.write("\t")
                handle.write(dish_id(run_id, user_index, row_index))
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


def user_insert_statement(users):
    values = []
    for user_id in users:
        values.append(
            "("
            + ", ".join(
                (
                    sql_string(user_id),
                    sql_string(f"perf user {user_id}"),
                    "NULL",
                    "NOW()",
                    "NOW()",
                )
            )
            + ")"
        )
    return (
        "INSERT INTO `user` (uid, nickname, avatar_file_id, created_at, updated_at)\nVALUES\n"
        + ",\n".join(values)
        + "\nON DUPLICATE KEY UPDATE nickname = VALUES(nickname), updated_at = NOW();\n"
    )


def write_prepare_sql(output, run_id, users, rows_per_user, batch_size, enabled):
    with output.open("w", encoding="utf-8") as handle:
        handle.write("SET NAMES utf8mb4;\n")
        handle.write("START TRANSACTION;\n")
        handle.write(user_insert_statement(users))
        handle.write(f"DELETE FROM dish_record WHERE {cleanup_condition(run_id)};\n")
        if enabled:
            all_rows = (
                payload(run_id, user_index, row_index, user_id)
                for user_index, user_id in enumerate(users)
                for row_index in range(rows_per_user)
            )
            batch = []
            for row in all_rows:
                batch.append(row)
                if len(batch) >= batch_size:
                    handle.write(insert_statement(batch))
                    batch = []
            if batch:
                handle.write(insert_statement(batch))
        handle.write("COMMIT;\n")


def write_cleanup_sql(output, run_id, users):
    user_list = ", ".join(sql_string(user_id) for user_id in users)
    output.write_text(
        "SET NAMES utf8mb4;\n"
        "START TRANSACTION;\n"
        f"DELETE FROM dish_record WHERE {cleanup_condition(run_id)};\n"
        f"DELETE FROM `user` WHERE uid IN ({user_list});\n"
        "COMMIT;\n",
        encoding="utf-8",
    )


def main():
    parser = argparse.ArgumentParser(description="Generate SQL and data-pool files for Locust API tests.")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--user-id-prefix", default="perf_user")
    parser.add_argument("--user-count", type=int, default=1)
    parser.add_argument("--targets", required=True, help="Comma-separated run targets, for example get_dish_detail,suite")
    parser.add_argument("--row-per-user", type=int, default=100)
    parser.add_argument("--payload-count", type=int, default=1000)
    parser.add_argument("--batch-size", type=int, default=500)
    args = parser.parse_args()
    if args.user_count < 1:
        parser.error("--user-count must be at least 1")
    if args.row_per_user < 0:
        parser.error("--row-per-user must be greater than or equal to 0")
    if args.payload_count < 0:
        parser.error("--payload-count must be greater than or equal to 0")

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    targets = [target.strip() for target in args.targets.split(",") if target.strip()]
    enabled = needs_rows(targets)

    prepare_sql = output_dir / "prepare.sql"
    cleanup_sql = output_dir / "cleanup.sql"
    users_file = output_dir / "user_ids.txt"
    dish_ids = output_dir / "dish_ids.txt"
    payloads = output_dir / "dish_payloads.jsonl"
    users = user_ids(args.user_id_prefix, args.user_count)

    write_prepare_sql(prepare_sql, args.run_id, users, args.row_per_user, args.batch_size, enabled)
    write_cleanup_sql(cleanup_sql, args.run_id, users)
    write_users(users_file, users)
    write_ids(dish_ids, args.run_id, users, args.row_per_user, enabled)
    write_payloads(payloads, args.run_id, users, args.payload_count)

    print(f"prepare_sql={prepare_sql}")
    print(f"cleanup_sql={cleanup_sql}")
    print(f"user_ids={users_file}")
    print(f"dish_ids={dish_ids}")
    print(f"payloads={payloads}")
    print(f"prepared_users={len(users)}")
    print(f"row_per_user={args.row_per_user if enabled else 0}")
    print(f"prepared_rows={len(users) * args.row_per_user if enabled else 0}")


if __name__ == "__main__":
    main()
