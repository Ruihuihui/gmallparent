package com.atguigu.gmall.payment.service;

import com.alipay.api.AlipayApiException;

public interface AlipayService {

    String createaliPay(Long orderId) throws AlipayApiException;

    /**
     * 退款
     * @param orderId
     * @return
     */
    boolean refund(Long orderId);


    /**
     * 关闭支付宝交易记录
     * @param orderId
     * @return
     */
    Boolean closePay(Long orderId);

    /**
     * 是否在支付宝中有交易记录
     * @param orderId
     * @return
     */
    Boolean checkPayment(Long orderId);
}
