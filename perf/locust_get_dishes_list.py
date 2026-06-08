from locust import task

from common import API_PREFIX, DishMemoUser, dish_list_params, request_headers


class GetDishesListUser(DishMemoUser):
    @task
    def get_dishes_list(self):
        self.client.get(
            f"{API_PREFIX}/dishes",
            params=dish_list_params(),
            headers=request_headers(self.user_id),
            name="GET /dishes",
        )
