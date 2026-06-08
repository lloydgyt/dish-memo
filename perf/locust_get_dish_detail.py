from locust import task

from common import API_PREFIX, DishMemoUser, dish_id_pool, request_headers


class GetDishDetailUser(DishMemoUser):
    @task
    def get_dish_detail(self):
        dish_id = dish_id_pool.require_random(self.user_id)
        self.client.get(
            f"{API_PREFIX}/dishes/{dish_id}",
            headers=request_headers(self.user_id),
            name="GET /dishes/{dish_id}",
        )
