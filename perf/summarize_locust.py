#!/usr/bin/env python3
import argparse
import csv
from pathlib import Path


FIELDS = (
    "stage",
    "run",
    "request_name",
    "request_count",
    "failure_count",
    "qps",
    "avg_ms",
    "p50_ms",
    "p95_ms",
    "p99_ms",
)


def value(row, *names, default=""):
    for name in names:
        if name in row and row[name] != "":
            return row[name]
    return default


def summarize(stage_dir, output):
    rows = []
    for stats_file in sorted(stage_dir.glob("*/stats_stats.csv")):
        run_name = stats_file.parent.name
        with stats_file.open(newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                if row.get("Name") in ("Aggregated", "Total"):
                    continue
                rows.append({
                    "stage": stage_dir.name,
                    "run": run_name,
                    "request_name": row.get("Name", ""),
                    "request_count": value(row, "Request Count"),
                    "failure_count": value(row, "Failure Count"),
                    "qps": value(row, "Requests/s"),
                    "avg_ms": value(row, "Average Response Time"),
                    "p50_ms": value(row, "50%"),
                    "p95_ms": value(row, "95%"),
                    "p99_ms": value(row, "99%"),
                })

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)

    print(f"summary={output}")
    for row in rows:
        print(
            f"{row['stage']}/{row['run']} {row['request_name']} "
            f"count={row['request_count']} fail={row['failure_count']} "
            f"qps={row['qps']} avg_ms={row['avg_ms']} p95_ms={row['p95_ms']}"
        )


def main():
    parser = argparse.ArgumentParser(description="Summarize Locust CSV stats.")
    parser.add_argument("--stage-dir", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    summarize(Path(args.stage_dir), Path(args.output))


if __name__ == "__main__":
    main()
