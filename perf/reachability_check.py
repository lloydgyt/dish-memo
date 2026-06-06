#!/usr/bin/env python3
import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request


def request(host, api_prefix, user_id, method, path, body=None):
    url = f"{host.rstrip('/')}{api_prefix}{path}"
    data = None
    headers = {
        "X-WX-OPENID": user_id,
        "X-Request-Id": f"reachability-{int(time.time())}",
    }
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8")
        raise RuntimeError(f"{method} {path} failed with HTTP {exc.code}: {raw}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"{method} {path} failed: {exc.reason}") from exc


def assert_ok(host, api_prefix, user_id, method, path, body=None):
    status, response = request(host, api_prefix, user_id, method, path, body)
    if status < 200 or status >= 300:
        raise RuntimeError(f"{method} {path} failed with HTTP {status}")
    if response.get("code") != 0:
        raise RuntimeError(f"{method} {path} failed with response: {response}")
    return response


def payload(user_id, suffix, meal_type):
    return {
        "name": f"可达性菜品-{suffix}",
        "file_id": f"production/dish/{user_id}/reachability_{suffix}.jpg",
        "note": f"reachability {suffix}",
        "date": "2026-06-05",
        "meal_type": meal_type,
    }


def main():
    parser = argparse.ArgumentParser(description="Check API reachability with one deterministic workflow.")
    parser.add_argument("--host", required=True)
    parser.add_argument("--api-prefix", default="/api/v1")
    parser.add_argument("--user-id", default="perf_user_001")
    args = parser.parse_args()

    try:
        created = assert_ok(args.host, args.api_prefix, args.user_id, "POST", "/dishes", payload(args.user_id, "post", "dinner"))
        dish_id = created["data"]["id"]
        assert_ok(args.host, args.api_prefix, args.user_id, "GET", "/dishes?page_no=1&page_size=20")
        assert_ok(args.host, args.api_prefix, args.user_id, "GET", f"/dishes/{urllib.parse.quote(dish_id)}")
        assert_ok(args.host, args.api_prefix, args.user_id, "GET", "/recommendations/today-meals?meal_type=dinner&size=3&refresh_token=reachability")
        assert_ok(args.host, args.api_prefix, args.user_id, "PUT", f"/dishes/{urllib.parse.quote(dish_id)}", payload(args.user_id, "put", "lunch"))
        assert_ok(args.host, args.api_prefix, args.user_id, "DELETE", f"/dishes/{urllib.parse.quote(dish_id)}")

        mixed = assert_ok(args.host, args.api_prefix, args.user_id, "POST", "/dishes", payload(args.user_id, "mixed", "breakfast"))
        mixed_id = mixed["data"]["id"]
        assert_ok(args.host, args.api_prefix, args.user_id, "GET", "/dishes?page_no=1&page_size=20")
        assert_ok(args.host, args.api_prefix, args.user_id, "GET", f"/dishes/{urllib.parse.quote(mixed_id)}")
        assert_ok(args.host, args.api_prefix, args.user_id, "PUT", f"/dishes/{urllib.parse.quote(mixed_id)}", payload(args.user_id, "mixed-put", "breakfast"))
        assert_ok(args.host, args.api_prefix, args.user_id, "GET", "/recommendations/today-meals?meal_type=breakfast&size=3&refresh_token=mixed")
        assert_ok(args.host, args.api_prefix, args.user_id, "DELETE", f"/dishes/{urllib.parse.quote(mixed_id)}")
    except Exception as exc:
        print(f"REACHABILITY FAILED: {exc}")
        raise SystemExit(1) from exc

    print("REACHABILITY OK")


if __name__ == "__main__":
    main()
