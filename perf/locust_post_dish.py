from locust import task

from common import API_PREFIX, DEFAULT_USER_ID, DishMemoUser, random_dish_payload, request_headers


class PostDishUser(DishMemoUser):
    @task
    def post_dish(self):
        self.client.post(
            f"{API_PREFIX}/dishes",
            json=random_dish_payload(DEFAULT_USER_ID),
            headers=request_headers(DEFAULT_USER_ID),
            name="POST /dishes",
        )
