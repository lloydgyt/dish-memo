import itertools
import json
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


def _load_user_ids_from_env():
    ids = []
    user_file = os.getenv("LOCUST_USER_ID_FILE")
    if user_file:
        with open(user_file, encoding="utf-8") as handle:
            ids.extend(line.strip() for line in handle if line.strip())

    raw_ids = os.getenv("LOCUST_USER_IDS", "")
    ids.extend(item.strip() for item in raw_ids.split(",") if item.strip())

    return ids or [DEFAULT_USER_ID]


class UserPool:
    def __init__(self):
        self._ids = _load_user_ids_from_env()
        self._lock = threading.Lock()
        self._cycle = itertools.cycle(self._ids)

    def next(self):
        with self._lock:
            return next(self._cycle)


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


def _load_payloads_from_env():
    payload_file = os.getenv("LOCUST_DISH_PAYLOAD_FILE")
    if not payload_file:
        return {}
    payloads = {}
    with open(payload_file, encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            payload = json.loads(line)
            user_id = payload.pop("_user_id", DEFAULT_USER_ID)
            payloads.setdefault(user_id, []).append(payload)
    return payloads


class DishPayloadPool:
    def __init__(self):
        self._payloads_by_user = _load_payloads_from_env()
        self._lock = threading.Lock()
        self._indexes = {}

    def next(self, user_id=None):
        owner = user_id or DEFAULT_USER_ID
        payloads = self._payloads_by_user.get(owner)
        if not payloads:
            payloads = self._payloads_by_user.get(DEFAULT_USER_ID)
        if not payloads:
            return random_dish_payload(owner)
        with self._lock:
            index = self._indexes.get(owner, 0)
            payload = dict(payloads[index % len(payloads)])
            self._indexes[owner] = index + 1
            return payload


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
    ids_by_user = {}

    # when providing LOCUST_DISH_IDS (like [1,2,3,4,...]), not usual way to use it
    raw_ids = os.getenv("LOCUST_DISH_IDS", "")
    raw_items = [item.strip() for item in raw_ids.split(",") if item.strip()]
    if raw_items:
        ids_by_user[DEFAULT_USER_ID] = raw_items

    id_file = os.getenv("LOCUST_DISH_ID_FILE")
    if id_file:
        with open(id_file, encoding="utf-8") as handle:
            for line in handle:
                item = line.strip()
                if not item:
                    continue
                if "\t" in item:
                    user_id, dish_id = item.split("\t", 1)
                elif "," in item:
                    user_id, dish_id = item.split(",", 1)
                else:
                    user_id, dish_id = DEFAULT_USER_ID, item
                ids_by_user.setdefault(user_id.strip(), []).append(dish_id.strip())
    return ids_by_user


class DishIdPool:
    def __init__(self):
        self._ids_by_user = _load_ids_from_env()
        self._lock = threading.Lock()
        self._cycles = {
            user_id: itertools.cycle(ids)
            for user_id, ids in self._ids_by_user.items()
            if ids
        }

    def _ids_for(self, user_id):
        ids = self._ids_by_user.get(user_id or DEFAULT_USER_ID)
        if ids:
            return ids
        return self._ids_by_user.get(DEFAULT_USER_ID, [])

    def require_random(self, user_id=None):
        ids = self._ids_for(user_id)
        if not ids:
            raise RuntimeError("Set LOCUST_DISH_IDS or LOCUST_DISH_ID_FILE with existing dish ids.")
        return random.choice(ids)

    def require_next(self, user_id=None):
        owner = user_id or DEFAULT_USER_ID
        cycle = self._cycles.get(owner) or self._cycles.get(DEFAULT_USER_ID)
        if cycle is None:
            raise RuntimeError("Set LOCUST_DISH_IDS or LOCUST_DISH_ID_FILE with existing dish ids.")
        with self._lock:
            return next(cycle)

    def pop_once(self, user_id=None):
        with self._lock:
            ids = self._ids_for(user_id)
            if not ids:
                return None
            return ids.pop()


user_pool = UserPool()
dish_id_pool = DishIdPool()
dish_payload_pool = DishPayloadPool()


_created_id_lock = threading.Lock()


def append_created_dish_id(dish_id):
    output_file = os.getenv("LOCUST_CREATED_DISH_ID_FILE")
    if not output_file or not dish_id:
        return
    with _created_id_lock:
        with open(output_file, "a", encoding="utf-8") as handle:
            handle.write(dish_id)
            handle.write("\n")


class DishMemoUser(HttpUser):
    abstract = True

    wait_time = between(
        float(os.getenv("LOCUST_WAIT_MIN", "1")),
        float(os.getenv("LOCUST_WAIT_MAX", "10")),
    )

    def __init__(self, environment):
        super().__init__(environment)
        self.user_id = user_pool.next()
