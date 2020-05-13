package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

@Component
public class OrderReceiver {
    @Autowired
    private RabbitService rabbitService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        if (null != orderId) {
            // 根据订单Id 查询订单表中是否有当前记录
            OrderInfo orderInfo = orderService.getById(orderId);
            if(null!=orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                //判断电商的加以记录  有记录 未付款 才可以关闭
                //交易记录表有数据 用户走到了二维码
                if(null!=paymentInfo&&paymentInfo.getPaymentStatus().equals(ProcessStatus.UNPAID.name())){
                    //关闭交易  检查支付宝是否有交易记录 用户是否扫了二维码
                    Boolean flag = paymentFeignClient.checkPayment(orderId);
                    if(flag){
                        //用户扫描了 未支付 要关闭支付宝
                        Boolean result = paymentFeignClient.closePay(orderId);
                        if(result){
                            //关闭成功支付宝 继续关闭订单
                            orderService.execExpiredOrder(orderId,"2");

                        }else {
                            //用户付款了 关闭不了
                            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,orderId);
                        }
                    }else {
                        //用户没有扫描二维码
                        //关闭订单
                        orderService.execExpiredOrder(orderId,"2");
                    }

                }else {
                    //paymentInfo中没有数据
                    orderService.execExpiredOrder(orderId,"1");
                }

            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }

    //更新订单
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void getMsg(Long orderId,Message message,Channel channel) throws IOException {
        //判断orderId 不能为空
        if(null!=orderId){
            //判断支付状态是未付款
            OrderInfo orderInfo = orderService.getById(orderId);
            if(null!=orderInfo){
                if(orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                    orderService.updateOrderStatus(orderId,ProcessStatus.PAID);
                    // 发送消息，通知仓库  减库存
                    orderService.sendOrderStatus(orderId);
                }
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }
    //监听库存系统减库存的消息对列
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updateOrderStatus(String msgJson,Message message,Channel channel){
        if(!StringUtils.isEmpty(msgJson)){
            //msgJson 是map转换成的  我再转换回去
            Map map = JSON.parseObject(msgJson, Map.class);
            String orderId = (String)map.get("orderId");
            String status = (String)map.get("status");
            //判断减库存是否成功
            if("DEDUCTED".equals(status)){
                //减库存成功 状态改为  等待发货
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.WAITING_DELEVER );
            }else {
                //减库存失败  ，发生超 卖
                //1，调用其他仓库的库存  从其他仓库进行补货
                //补货失败  人工介入  与客户进行沟通 能否晚点发货
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.STOCK_EXCEPTION);
            }
        }
    }
}
