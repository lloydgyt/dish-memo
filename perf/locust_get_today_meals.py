from locust import task

from common import API_PREFIX, DishMemoUser, recommendation_params, request_headers


class GetTodayMealsUser(DishMemoUser):
    @task
    def get_today_meals(self):
        self.client.get(
            f"{API_PREFIX}/recommendations/today-meals",
            params=recommendation_params(),
            headers=request_headers(),
            name="GET /recommendations/today-meals",
        )
