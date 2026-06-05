import itertools
import os
import random
import threading
import uuid
from datetime import date, timedelta

from locust import HttpUser, between


API_PREFIX = os.getenv("LOCUST_API_PREFIX", "/api/v1")
DEFAULT_USER_ID = os.getenv("LOCUST_USER_ID", "perf_user_001")
DEFAULT_MEAL_TYPES = ("breakfast", "lunch", "dinner")
DEFAULT_DISH_NAMES = (
    "番茄炒蛋",
    "青椒肉丝",
    "香煎鸡胸",
    "三明治",
    "牛奶麦片",
    "清炒西兰花",
    "红烧排骨",
    "虾仁炒饭",
)


def request_headers(user_id=None):
    return {
        "X-WX-OPENID": user_id or DEFAULT_USER_ID,
        "X-Request-Id": f"locust-{uuid.uuid4().hex}",
    }


def random_meal_type():
    raw = os.getenv("LOCUST_MEAL_TYPE")
    if raw:
        return raw
    return random.choice(DEFAULT_MEAL_TYPES)


def random_dish_payload(user_id=None):
    owner = user_id or DEFAULT_USER_ID
    suffix = uuid.uuid4().hex[:12]
    meal_type = random_meal_type()
    dish_day = date.today() - timedelta(days=random.randint(0, 90))
    return {
        "name": f"{random.choice(DEFAULT_DISH_NAMES)}-{suffix[:6]}",
        "file_id": f"production/dish/{owner}/perf_{suffix}.jpg",
        "note": f"locust perf payload {suffix}",
        "date": dish_day.isoformat(),
        "meal_type": meal_type,
    }


def dish_list_params():
    params = {
        "page_no": int(os.getenv("LOCUST_PAGE_NO", "1")),
        "page_size": int(os.getenv("LOCUST_PAGE_SIZE", "20")),
    }
    meal_type = os.getenv("LOCUST_MEAL_TYPE")
    if meal_type:
        params["meal_type"] = meal_type
    keyword = os.getenv("LOCUST_KEYWORD")
    if keyword:
        params["keyword"] = keyword
    date_from = os.getenv("LOCUST_DATE_FROM")
    if date_from:
        params["date_from"] = date_from
    date_to = os.getenv("LOCUST_DATE_TO")
    if date_to:
        params["date_to"] = date_to
    return params


def recommendation_params():
    return {
        "meal_type": random_meal_type(),
        "size": int(os.getenv("LOCUST_RECOMMENDATION_SIZE", "3")),
        "refresh_token": f"r_{uuid.uuid4().hex[:12]}",
    }


def _load_ids_from_env():
    ids = []
    raw_ids = os.getenv("LOCUST_DISH_IDS", "")
    ids.extend(item.strip() for item in raw_ids.split(",") if item.strip())

    id_file = os.getenv("LOCUST_DISH_ID_FILE")
    if id_file:
        with open(id_file, encoding="utf-8") as handle:
            ids.extend(line.strip() for line in handle if line.strip())
    return ids


class DishIdPool:
    def __init__(self):
        self._ids = _load_ids_from_env()
        self._lock = threading.Lock()
        self._cycle = itertools.cycle(self._ids) if self._ids else None

    def require_random(self):
        if not self._ids:
            raise RuntimeError("Set LOCUST_DISH_IDS or LOCUST_DISH_ID_FILE with existing dish ids.")
        return random.choice(self._ids)

    def require_next(self):
        if self._cycle is None:
            raise RuntimeError("Set LOCUST_DISH_IDS or LOCUST_DISH_ID_FILE with existing dish ids.")
        with self._lock:
            return next(self._cycle)

    def pop_once(self):
        with self._lock:
            if not self._ids:
                return None
            return self._ids.pop()


dish_id_pool = DishIdPool()


class DishMemoUser(HttpUser):
    abstract = True

    wait_time = between(
        float(os.getenv("LOCUST_WAIT_MIN", "1")),
        float(os.getenv("LOCUST_WAIT_MAX", "10")),
    )
