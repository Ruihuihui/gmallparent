package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface OrderService extends IService<OrderInfo> {
    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    Long saveOrderInfo(OrderInfo orderInfo);

    /**
     * 生成流水号 同时放入缓存
     * @param userId
     * @return
     */

    String getTradeNo(String userId);

    /**
     * 比较流水号
     * @param tradeNo  页面的流水号
     * @param userId  //获取缓存的流水号
     * @return
     */

    boolean chechTradeNo(String tradeNo,String userId);

    /**
     * 删除缓存的流水号
     * @param userId
     */
    void deleteTradeNo(String userId);

    /**
     * 判断库存
     * @param skuId
     * @param skuNum
     * @return
     */
    boolean chechStock(Long skuId, Integer skuNum);


    /**
     * 根据订单Id 查询订单信息
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(Long orderId);

    /**
     * 根据订单id关闭过期订单
     * @param orderId
     */
    void execExpiredOrder(Long orderId);

    void updateOrderStatus(Long orderId, ProcessStatus closed);
    // 发送消息，通知仓库改库存
    void sendOrderStatus(Long orderId);

    /**
     * 将orderInfo 变为map
     * @param orderInfo
     * @return
     */
    Map initWareOrder(OrderInfo orderInfo);

    List<OrderInfo> orderSplit(long orderId, String wareSkuMap);

    /**
     * 关闭过期订单
     * @param orderId
     * @param s
     */
    void execExpiredOrder(Long orderId, String flag);
}
