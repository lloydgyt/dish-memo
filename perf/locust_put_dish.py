from locust import task

from common import API_PREFIX, DishMemoUser, dish_id_pool, dish_payload_pool, request_headers


class PutDishUser(DishMemoUser):
    @task
    def put_dish(self):
        dish_id = dish_id_pool.require_next(self.user_id)
        self.client.put(
            f"{API_PREFIX}/dishes/{dish_id}",
            json=dish_payload_pool.next(self.user_id),
            headers=request_headers(self.user_id),
            name="PUT /dishes/{dish_id}",
        )
