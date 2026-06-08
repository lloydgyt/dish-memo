import random

from locust import task

from common import (
    API_PREFIX,
    DEFAULT_USER_ID,
    DishMemoUser,
    dish_id_pool,
    dish_payload_pool,
    dish_list_params,
    recommendation_params,
    request_headers,
)


class MixedDishBehaviorUser(DishMemoUser):
    def on_start(self):
        self.owned_dish_ids = []

    @task(4)
    def list_dishes(self):
        self.client.get(
            f"{API_PREFIX}/dishes",
            params=dish_list_params(),
            headers=request_headers(DEFAULT_USER_ID),
            name="GET /dishes",
        )

    @task(3)
    def get_today_meals(self):
        self.client.get(
            f"{API_PREFIX}/recommendations/today-meals",
            params=recommendation_params(),
            headers=request_headers(DEFAULT_USER_ID),
            name="GET /recommendations/today-meals",
        )

    @task(3)
    def create_dish_for_workflow(self):
        with self.client.post(
            f"{API_PREFIX}/dishes",
            json=dish_payload_pool.next(DEFAULT_USER_ID),
            headers=request_headers(DEFAULT_USER_ID),
            name="POST /dishes",
            catch_response=True,
        ) as response:
            dish_id = _extract_dish_id(response)
            if dish_id:
                self.owned_dish_ids.append(dish_id)
            else:
                response.failure("missing data.id in create response")

    @task(2)
    def update_owned_dish(self):
        dish_id = dish_id_pool.require_next()
        self.client.put(
            f"{API_PREFIX}/dishes/{dish_id}",
            json=dish_payload_pool.next(DEFAULT_USER_ID),
            headers=request_headers(DEFAULT_USER_ID),
            name="PUT /dishes/{dish_id}",
        )

    @task(2)
    def get_owned_dish_detail(self):
        dish_id = dish_id_pool.require_random()
        self.client.get(
            f"{API_PREFIX}/dishes/{dish_id}",
            headers=request_headers(DEFAULT_USER_ID),
            name="GET /dishes/{dish_id}",
        )

    @task(1)
    def delete_owned_dish(self):
        if not self.owned_dish_ids:
            return
        dish_id = self.owned_dish_ids.pop(random.randrange(len(self.owned_dish_ids)))
        self.client.delete(
            f"{API_PREFIX}/dishes/{dish_id}",
            headers=request_headers(DEFAULT_USER_ID),
            name="DELETE /dishes/{dish_id}",
        )


def _extract_dish_id(response):
    try:
        body = response.json()
    except ValueError:
        return None
    data = body.get("data") if isinstance(body, dict) else None
    if not isinstance(data, dict):
        return None
    return data.get("id")
