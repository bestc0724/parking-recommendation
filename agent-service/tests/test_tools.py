"""
Tool 测试 — mock httpx，验证各工具的成功/失败路径
"""
from unittest.mock import MagicMock, patch

from tools.recommend_tools import recommend_spot, query_available_spots
from tools.reservation_tools import reserve_spot, cancel_reservation


def _mock_resp(payload: dict) -> MagicMock:
    resp = MagicMock()
    resp.json.return_value = payload
    return resp


class TestRecommendSpot:
    @patch("tools.recommend_tools.httpx.post")
    def test_success_returns_top3(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "success",
            "totalAvailable": 20,
            "recommendations": [
                {"spotId": "S001", "heightLimit": 2.5, "hasCharging": True,
                 "distanceToElevator": 10, "pricePerHour": 5.0, "score": 0.9,
                 "reason": "符合全部需求"},
                {"spotId": "S007", "heightLimit": 2.3, "hasCharging": True,
                 "distanceToElevator": 30, "pricePerHour": 4.0, "score": 0.7,
                 "reason": "价格更低"},
            ],
        })
        result = recommend_spot.invoke({"query": "有充电桩", "user_id": "U1"})
        assert "S001" in result
        assert "S007" in result
        assert "充电桩" in result

    @patch("tools.recommend_tools.httpx.post")
    def test_no_match_mentions_conditions(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "no_match",
            "message": "无匹配",
            "parsed": {"hard_conditions": [{"type": "height", "value": "2.0"}]},
        })
        result = recommend_spot.invoke({"query": "限高2米", "user_id": ""})
        assert "没有找到" in result
        assert "放宽" in result

    @patch("tools.recommend_tools.httpx.post")
    def test_no_available(self, mock_post):
        mock_post.return_value = _mock_resp({"status": "no_available", "message": "无车位"})
        result = recommend_spot.invoke({"query": "x", "user_id": ""})
        assert "没有可用车位" in result

    @patch("tools.recommend_tools.httpx.post")
    def test_http_error(self, mock_post):
        mock_post.side_effect = Exception("connection refused")
        result = recommend_spot.invoke({"query": "x", "user_id": ""})
        assert "失败" in result


class TestReservationTools:
    @patch("tools.reservation_tools.httpx.post")
    def test_reserve_success(self, mock_post):
        mock_post.return_value = _mock_resp({
            "status": "reserved",
            "reservationId": "R1A2B3C4",
            "spotId": "S001",
            "expiresAt": "2026-09-24T12:00:00",
        })
        result = reserve_spot.invoke({"user_id": "U1", "spot_id": "S001"})
        assert "R1A2B3C4" in result
        assert "1小时" in result

    @patch("tools.reservation_tools.httpx.post")
    def test_reserve_spot_taken(self, mock_post):
        mock_post.return_value = _mock_resp({"status": "error", "error": "车位已被预约"})
        result = reserve_spot.invoke({"user_id": "U1", "spot_id": "S001"})
        assert "失败" in result
        assert "已被预约" in result

    @patch("tools.reservation_tools.httpx.post")
    def test_reserve_http_error(self, mock_post):
        mock_post.side_effect = Exception("timeout")
        result = reserve_spot.invoke({"user_id": "U1", "spot_id": "S001"})
        assert "失败" in result

    @patch("tools.reservation_tools.httpx.delete")
    def test_cancel_success(self, mock_delete):
        mock_delete.return_value = _mock_resp({"status": "cancelled", "reservationId": "R1"})
        result = cancel_reservation.invoke({"reservation_id": "R1"})
        assert "已取消" in result

    @patch("tools.reservation_tools.httpx.delete")
    def test_cancel_invalid_status(self, mock_delete):
        mock_delete.return_value = _mock_resp({"status": "error", "error": "预约状态不可取消"})
        result = cancel_reservation.invoke({"reservation_id": "R1"})
        assert "失败" in result


class TestQueryAvailableSpots:
    @patch("tools.recommend_tools.httpx.get")
    def test_success(self, mock_get):
        mock_get.return_value = _mock_resp({
            "total": 2,
            "spots": [
                {"spotId": "S001", "hasCharging": True, "spotType": "standard",
                 "heightLimit": 2.5, "distanceToElevator": 10, "pricePerHour": 5.0},
                {"spotId": "S002", "hasCharging": False, "spotType": "large",
                 "heightLimit": 3.0, "distanceToElevator": 50, "pricePerHour": 4.0},
            ],
        })
        result = query_available_spots.invoke({})
        assert "2" in result
        assert "S001" in result

    @patch("tools.recommend_tools.httpx.get")
    def test_empty(self, mock_get):
        mock_get.return_value = _mock_resp({"total": 0, "spots": []})
        result = query_available_spots.invoke({})
        assert "没有可用车位" in result
