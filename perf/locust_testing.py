from locust import task

from common import API_PREFIX, DishMemoUser, dish_list_params, request_headers


class TestingUser(DishMemoUser):
    @task
    def testing(self):
        self.client.get(
            f"{API_PREFIX}/testing",
            params=dish_list_params(),
            headers=request_headers(self.user_id),
            name="GET /testing",
        )
