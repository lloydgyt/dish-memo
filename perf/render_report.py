#!/usr/bin/env python3
import argparse
import csv
from collections import defaultdict
from pathlib import Path


def parse_float(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def parse_int(value):
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return 0


def read_rows(summary_files):
    rows = []
    for summary_file in summary_files:
        with Path(summary_file).open(newline="", encoding="utf-8") as handle:
            rows.extend(csv.DictReader(handle))
    return rows


def weighted_average(rows, field):
    total_count = sum(parse_int(row.get("request_count")) for row in rows)
    if total_count == 0:
        return 0.0
    weighted = sum(parse_float(row.get(field)) * parse_int(row.get("request_count")) for row in rows)
    return weighted / total_count


def max_percentile(rows, field):
    return max((parse_float(row.get(field)) for row in rows), default=0.0)


def fmt_number(value):
    if abs(value - round(value)) < 0.001:
        return str(int(round(value)))
    return f"{value:.2f}"


def metrics(rows):
    total_requests = sum(parse_int(row.get("request_count")) for row in rows)
    total_failures = sum(parse_int(row.get("failure_count")) for row in rows)
    failure_rate = (total_failures / total_requests * 100) if total_requests else 0.0
    return {
        "total_requests": total_requests,
        "failure_rate": failure_rate,
        "avg_ms": weighted_average(rows, "avg_ms"),
        "p50_ms": max_percentile(rows, "p50_ms"),
        "p95_ms": max_percentile(rows, "p95_ms"),
        "p99_ms": max_percentile(rows, "p99_ms"),
        "rps": sum(parse_float(row.get("qps")) for row in rows),
    }


def render_metric_row(name, values):
    return (
        f"| {name} | {values['total_requests']} | {values['failure_rate']:.2f}% | "
        f"{fmt_number(values['avg_ms'])} ms | {fmt_number(values['p50_ms'])} ms | "
        f"{fmt_number(values['p95_ms'])} ms | {fmt_number(values['p99_ms'])} ms | "
        f"{fmt_number(values['rps'])} |"
    )


def group_by_run(rows):
    grouped = defaultdict(list)
    for row in rows:
        grouped[row.get("run", "")].append(row)
    return dict(sorted(grouped.items()))


def render_report(args):
    rows = read_rows(args.summary)
    grouped_rows = group_by_run(rows)
    overall = metrics(rows)

    title = args.title or f"{args.stage.capitalize()} Test Summary"
    lines = [
        f"# {title}",
        "",
        "## Test Setup",
        "",
        f"- Host: {args.host}",
        f"- Users: {args.users}",
        f"- Spawn rate: {args.spawn_rate}/s",
        f"- Duration: {args.run_time}",
        f"- Stage: {args.stage}",
        f"- Commit: {args.commit}",
        "",
        "## Overall Result",
        "",
        "| Metric | Value |",
        "|---|---:|",
        f"| Total requests | {overall['total_requests']} |",
        f"| Failure rate | {overall['failure_rate']:.2f}% |",
        f"| Average response time | {fmt_number(overall['avg_ms'])} ms |",
        f"| p50 | {fmt_number(overall['p50_ms'])} ms |",
        f"| p95 | {fmt_number(overall['p95_ms'])} ms |",
        f"| p99 | {fmt_number(overall['p99_ms'])} ms |",
        f"| RPS | {fmt_number(overall['rps'])} |",
        "",
        "## Module Summary",
        "",
        "| Module | Requests | Failure rate | Avg | p50 | p95 | p99 | RPS |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]

    for run_name, run_rows in grouped_rows.items():
        lines.append(render_metric_row(run_name, metrics(run_rows)))

    lines.extend(["", "## Request Details", ""])
    for run_name, run_rows in grouped_rows.items():
        lines.extend([
            f"### {run_name}",
            "",
            "| Request | Count | Failures | QPS | Avg | p50 | p95 | p99 |",
            "|---|---:|---:|---:|---:|---:|---:|---:|",
        ])
        for row in run_rows:
            lines.append(
                f"| {row.get('request_name', '')} | "
                f"{parse_int(row.get('request_count'))} | "
                f"{parse_int(row.get('failure_count'))} | "
                f"{fmt_number(parse_float(row.get('qps')))} | "
                f"{fmt_number(parse_float(row.get('avg_ms')))} ms | "
                f"{fmt_number(parse_float(row.get('p50_ms')))} ms | "
                f"{fmt_number(parse_float(row.get('p95_ms')))} ms | "
                f"{fmt_number(parse_float(row.get('p99_ms')))} ms |"
            )
        lines.append("")

    lines.extend([
        "## Bottlenecks（用户填写）",
        "",
        "- ",
        "",
        "## Conclusion（用户填写）",
        "",
        "待填写。",
        "",
    ])

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"report={output}")


def main():
    parser = argparse.ArgumentParser(
        description="Render a Markdown report from module-level Locust summary CSV files."
    )
    parser.add_argument("--summary", action="append", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--host", required=True)
    parser.add_argument("--users", required=True)
    parser.add_argument("--spawn-rate", required=True)
    parser.add_argument("--run-time", required=True)
    parser.add_argument("--stage", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--title")
    render_report(parser.parse_args())


if __name__ == "__main__":
    main()
