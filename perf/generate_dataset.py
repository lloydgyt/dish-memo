#!/usr/bin/env python3
import argparse
import json
from datetime import date, timedelta
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


def payload(index, user_id):
    meal_type = MEAL_TYPES[index % len(MEAL_TYPES)]
    dish_day = date(2026, 6, 5) - timedelta(days=index % 90)
    suffix = f"{index:06d}"
    return {
        "name": f"{DISH_NAMES[index % len(DISH_NAMES)]}-{suffix}",
        "file_id": f"production/dish/{user_id}/perf_dataset_{suffix}.jpg",
        "note": f"perf dataset item {suffix}",
        "date": dish_day.isoformat(),
        "meal_type": meal_type,
    }


def main():
    parser = argparse.ArgumentParser(description="Generate deterministic Locust dish payload dataset.")
    parser.add_argument("--output", required=True)
    parser.add_argument("--created-ids-output", required=True)
    parser.add_argument("--count", type=int, default=1000)
    parser.add_argument("--user-id", default="perf_user_001")
    args = parser.parse_args()

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for index in range(args.count):
            handle.write(json.dumps(payload(index, args.user_id), ensure_ascii=False))
            handle.write("\n")

    created_ids_output = Path(args.created_ids_output)
    created_ids_output.parent.mkdir(parents=True, exist_ok=True)
    created_ids_output.write_text("", encoding="utf-8")

    print(f"dataset={output}")
    print(f"created_ids={created_ids_output}")
    print(f"count={args.count}")


if __name__ == "__main__":
    main()
