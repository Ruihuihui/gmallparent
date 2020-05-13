package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;

import java.util.Map;

public interface PaymentService {


    /**
     * 保存交易记录
     * @param orderInfo
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    void savePaymentInfo(OrderInfo orderInfo, String paymentType);

    /**
     * 根据out_trade_no  和付款方式  查询 交易记录
     * @param out_trade_no
     * @param name   支付宝 或微信
     */
    PaymentInfo getPaymentInfo(String out_trade_no, String name);

    /**
     * 根据out_trade_no 付款方式 更新交易记录
     * @param out_trade_no
     * @param name
     * @param paramMap
     */
    void paySuccess(String out_trade_no, String name, Map<String, String> paramMap);

    void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfo);

    /**
     * 关闭交易记录
     * @param orderId
     */
    void closePayment(Long orderId);
}
