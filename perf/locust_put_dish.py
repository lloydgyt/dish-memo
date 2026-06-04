from locust import task

from common import API_PREFIX, DEFAULT_USER_ID, DishMemoUser, dish_id_pool, random_dish_payload, request_headers


class PutDishUser(DishMemoUser):
    @task
    def put_dish(self):
        dish_id = dish_id_pool.require_next()
        self.client.put(
            f"{API_PREFIX}/dishes/{dish_id}",
            json=random_dish_payload(DEFAULT_USER_ID),
            headers=request_headers(DEFAULT_USER_ID),
            name="PUT /dishes/{dish_id}",
        )
