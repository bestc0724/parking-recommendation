package com.silenker.parkingrecommendation.mapper;

import com.silenker.parkingrecommendation.model.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    Order selectById(@Param("orderId") String orderId);

    /** 查询某预约下进行中的订单（status='parking'），用于幂等与结算 */
    Order selectActiveByReservationId(@Param("reservationId") String reservationId);

    List<Order> selectByUserId(@Param("userId") String userId);

    int insert(Order order);

    /** 结算离场：更新状态、离场时间、费用明细 */
    int updateCheckout(Order order);

    int updatePayment(@Param("orderId") String orderId,
                      @Param("paymentStatus") String paymentStatus,
                      @Param("paidAt") LocalDateTime paidAt);
}
