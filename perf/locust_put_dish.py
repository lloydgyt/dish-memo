from locust import task

from common import API_PREFIX, DEFAULT_USER_ID, DishMemoUser, dish_id_pool, dish_payload_pool, request_headers


class PutDishUser(DishMemoUser):
    @task
    def put_dish(self):
        dish_id = dish_id_pool.require_next()
        self.client.put(
            f"{API_PREFIX}/dishes/{dish_id}",
            json=dish_payload_pool.next(DEFAULT_USER_ID),
            headers=request_headers(DEFAULT_USER_ID),
            name="PUT /dishes/{dish_id}",
        )
