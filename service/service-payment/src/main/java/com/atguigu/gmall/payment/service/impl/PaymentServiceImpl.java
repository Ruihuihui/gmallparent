package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;
    @Autowired
    private RabbitService rabbitService;

    /**
     * 保存交易记录
     * @param orderInfo
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    @Override
    public void savePaymentInfo(OrderInfo orderInfo, String paymentType) {
        //paymentInfo记录当前一个订单的支付状态
        //一个订单只能有一个交易记录
        //不能出现 当前一个订单 有支付宝的两条交易记录
        //确保多人支付时 只能有一个成功
        Integer count = paymentInfoMapper.selectCount(new QueryWrapper<PaymentInfo>().eq("order_id", orderInfo.getId()).eq("payment_type", paymentType));
        if(count>0)return;


        //创建一个paymentInfo对象
        PaymentInfo paymentInfo = new PaymentInfo();
        //当前订单创建时间
        paymentInfo.setCreateTime(new Date());
        //当前订单的orderId
        paymentInfo.setOrderId(orderInfo.getId());
        //当前订单的交易类型   支付宝  or  微信
        paymentInfo.setPaymentType(paymentType);
        //当前订单的 流水号
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        //当前订单的支付状态   支付中
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());
        //当前订单的描述
        paymentInfo.setSubject(orderInfo.getTradeBody());
        //paymentInfo.setSubject("test");
        //当前订单的总金额
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfoMapper.insert(paymentInfo);
    }

    /**
     * 根据out_trade_no  和付款方式查询 交易记录
     * @param out_trade_no
     * @param name 支付宝 或微信
     * @return
     */
    @Override
    public PaymentInfo getPaymentInfo(String out_trade_no, String name) {
        //查询支付详情表
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        //通过订单编号和支付方式得到唯一  信息 并返回
        paymentInfoQueryWrapper.eq("out_trade_no",out_trade_no).eq("payment_type",name);
        return  paymentInfoMapper.selectOne(paymentInfoQueryWrapper);

    }

    /**  支付成功 修改订单状态
     * 根据out_trade_no 付款方式 更新交易记录
     * @param out_trade_no
     * @param name
     * @param paramMap
     */
    @Override
    public void paySuccess(String out_trade_no, String name, Map<String, String> paramMap) {
        PaymentInfo paymentInfo = new PaymentInfo();

        paymentInfo.setPaymentStatus(PaymentStatus.PAID.name());
        paymentInfo.setCallbackTime(new Date());
        paymentInfo.setCallbackContent(paramMap.toString());
        String trade_no = paramMap.get("trade_no");
        paymentInfo.setTradeNo(trade_no);
        //根据 支付编号 和支付方式 把要修改的支付成功后的内容  update
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("out_trade_no",out_trade_no).eq("payment_type",name);
        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);
        //    rabbit
        //支付成功之后  发送消息通知订单
        PaymentInfo paymentInfoq1= getPaymentInfo(out_trade_no, name);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,paymentInfoq1.getOrderId());
    }

    @Override
    public void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfo) {
        paymentInfoMapper.update(paymentInfo,new QueryWrapper<PaymentInfo>().eq("out_trade_no",outTradeNo));
    }


    /**
     * 关闭交易记录
     * @param orderId
     */
    @Override
    public void closePayment(Long orderId) {
        //交易记录表中的数据什么时候产生
        //用户点击支付宝生成二维码的时候
        //只下单 不点击生成二维码 没有数据
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderId);

        //关单之前要查询是否已经生成二维码交易记录
        Integer count = paymentInfoMapper.selectCount(paymentInfoQueryWrapper);
        //如果交易记录中没数据    则返回    不执行关闭
        if(null==count||count.intValue()==0){
            return;
        }
        //第一个填更新内容，第二个填更新条件
        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);
    }
}
