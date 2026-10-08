"""
停车订单工具测试 — mock httpx，验证结算/支付/查询的各路径
"""
from unittest.mock import MagicMock, patch

from tools.order_tools import checkout_order, pay_order, query_my_orders
from tools.vehicle_tools import add_vehicle, query_my_vehicles


def _mock_resp(payload: dict) -> MagicMock:
    resp = MagicMock()
    resp.json.return_value = payload
    return resp


class TestCheckoutOrder:
    @patch("tools.order_tools.httpx.post")
    def test_checkout_success(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "completed",
            "orderId": "O1A2B3C4",
            "parkingFee": "15.00",
            "chargingFee": "24.00",
            "totalAmount": "39.00",
            "paymentStatus": "unpaid",
        })
        result = checkout_order.invoke({"reservation_id": "R1", "charging_kwh": 20})
        assert "O1A2B3C4" in result
        assert "15.00" in result
        assert "24.00" in result
        assert "39.00" in result

    @patch("tools.order_tools.httpx.post")
    def test_checkout_no_charging(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "completed",
            "orderId": "O1A2B3C4",
            "parkingFee": "10.00",
            "chargingFee": "0.00",
            "totalAmount": "10.00",
            "paymentStatus": "unpaid",
        })
        result = checkout_order.invoke({"reservation_id": "R1", "charging_kwh": 0})
        assert "0.00" in result
        assert "10.00" in result

    @patch("tools.order_tools.httpx.post")
    def test_checkout_no_active_order(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "error", "error": "没有进行中的停车订单"
        })
        result = checkout_order.invoke({"reservation_id": "R9", "charging_kwh": 0})
        assert "失败" in result

    @patch("tools.order_tools.httpx.post")
    def test_checkout_http_error(self, mock_post):
        mock_post.side_effect = Exception("timeout")
        result = checkout_order.invoke({"reservation_id": "R1", "charging_kwh": 0})
        assert "失败" in result


class TestPayOrder:
    @patch("tools.order_tools.httpx.post")
    def test_pay_success(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "paid", "orderId": "O1", "amount": "39.00"
        })
        result = pay_order.invoke({"order_id": "O1"})
        assert "支付成功" in result
        assert "39.00" in result

    @patch("tools.order_tools.httpx.post")
    def test_pay_already_paid(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "error", "error": "订单已支付，请勿重复支付"
        })
        result = pay_order.invoke({"order_id": "O1"})
        assert "失败" in result

    @patch("tools.order_tools.httpx.post")
    def test_pay_http_error(self, mock_post):
        mock_post.side_effect = Exception("connection refused")
        result = pay_order.invoke({"order_id": "O1"})
        assert "失败" in result


class TestQueryMyOrders:
    @patch("tools.order_tools.httpx.get")
    def test_query_with_orders(self, mock_get):
        mock_get.return_value = _mock_resp({
            "total": 2,
            "orders": [
                {"orderId": "O1", "spotId": "S001", "status": "completed",
                 "totalAmount": "39.00", "paymentStatus": "paid"},
                {"orderId": "O2", "spotId": "S002", "status": "parking",
                 "totalAmount": "0.00", "paymentStatus": "unpaid"},
            ],
        })
        result = query_my_orders.invoke({"user_id": "U1"})
        assert "O1" in result
        assert "O2" in result
        assert "停车中" in result
        assert "已支付" in result

    @patch("tools.order_tools.httpx.get")
    def test_query_empty(self, mock_get):
        mock_get.return_value = _mock_resp({"total": 0, "orders": []})
        result = query_my_orders.invoke({"user_id": "U1"})
        assert "没有任何停车订单" in result


class TestVehicleTools:
    @patch("tools.vehicle_tools.httpx.post")
    def test_add_vehicle(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "added", "vehicleId": "V001", "plateNumber": "京A12345"
        })
        result = add_vehicle.invoke({
            "user_id": "U1", "plate_number": "京A12345",
            "model": "特斯拉", "vehicle_height": 1.44, "is_ev": True,
        })
        assert "添加成功" in result
        assert "V001" in result

    @patch("tools.vehicle_tools.httpx.get")
    def test_query_vehicles(self, mock_get):
        mock_get.return_value = _mock_resp({
            "total": 1,
            "vehicles": [
                {"vehicleId": "V001", "plateNumber": "京A12345",
                 "model": "特斯拉", "vehicleHeight": 1.44, "isEv": True},
            ],
        })
        result = query_my_vehicles.invoke({"user_id": "U1"})
        assert "V001" in result
        assert "新能源" in result

    @patch("tools.vehicle_tools.httpx.get")
    def test_query_vehicles_empty(self, mock_get):
        mock_get.return_value = _mock_resp({"total": 0, "vehicles": []})
        result = query_my_vehicles.invoke({"user_id": "U1"})
        assert "尚未添加" in result
