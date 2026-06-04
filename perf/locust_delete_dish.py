from locust import StopUser, task

from common import API_PREFIX, DishMemoUser, dish_id_pool, request_headers


class DeleteDishUser(DishMemoUser):
    @task
    def delete_dish(self):
        dish_id = dish_id_pool.pop_once()
        if dish_id is None:
            raise StopUser()
        self.client.delete(
            f"{API_PREFIX}/dishes/{dish_id}",
            headers=request_headers(),
            name="DELETE /dishes/{dish_id}",
        )
