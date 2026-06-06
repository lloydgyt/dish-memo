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


def collect_rows(stats_file, stage_name, run_name):
    rows = []
    with stats_file.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row.get("Name") in ("Aggregated", "Total"):
                continue
            rows.append({
                "stage": stage_name,
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
    return rows


def write_rows(rows, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def print_rows(rows, output):
    print(f"summary={output}")
    for row in rows:
        print(
            f"{row['stage']}/{row['run']} {row['request_name']} "
            f"count={row['request_count']} fail={row['failure_count']} "
            f"qps={row['qps']} avg_ms={row['avg_ms']} p95_ms={row['p95_ms']}"
        )


def summarize_run(run_dir, output, stage, run):
    stats_file = run_dir / "stats_stats.csv"
    rows = collect_rows(stats_file, stage, run)
    write_rows(rows, output)
    print_rows(rows, output)


def main():
    parser = argparse.ArgumentParser(description="Summarize one Locust run CSV stats file.")
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--stage", required=True)
    parser.add_argument("--run", required=True)
    args = parser.parse_args()
    summarize_run(Path(args.run_dir), Path(args.output), args.stage, args.run)


if __name__ == "__main__":
    main()
