from locust import task

from common import API_PREFIX, DEFAULT_USER_ID, DishMemoUser, append_created_dish_id, dish_payload_pool, request_headers


class PostDishUser(DishMemoUser):
    @task
    def post_dish(self):
        with self.client.post(
            f"{API_PREFIX}/dishes",
            json=dish_payload_pool.next(DEFAULT_USER_ID),
            headers=request_headers(DEFAULT_USER_ID),
            name="POST /dishes",
            catch_response=True,
        ) as response:
            dish_id = _extract_dish_id(response)
            if dish_id:
                append_created_dish_id(dish_id)
            else:
                response.failure("missing data.id in create response")


def _extract_dish_id(response):
    try:
        body = response.json()
    except ValueError:
        return None
    data = body.get("data") if isinstance(body, dict) else None
    if not isinstance(data, dict):
        return None
    return data.get("id")
