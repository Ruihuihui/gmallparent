package com.atguigu.gmall.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 发送消息的配置类
 */
@Component
@Slf4j
public class MQProducerAckConfig implements RabbitTemplate.ConfirmCallback,RabbitTemplate.ReturnCallback {
    //引入操作消息队列的模板
    @Autowired
    private RabbitTemplate rabbitTemplate;
    //初始化方法
    @PostConstruct
    private void init(){
        //初始化ConfirmCallback方法
        //this  时当前类   MQProducerAckConfig  继承了
        // RabbitTemplate.ConfirmCallback,RabbitTemplate.ReturnCallback
        //所以要初始化一下
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnCallback(this);
    }
    //消息没有发送到交换机会走  confirm  返回一个 ack = false
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        //判断ack == true  则消息成功发送到交换机
        if(ack){
            log.info("消息成功发送到交换机");
        }else {
            log.info("消息没有发送到交换机");
        }
    }

    //交换机与队列的绑定判定
    //如果消息从交换机正确绑定到队列，那么这个方法不会执行
    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        // 反序列化对象输出
        System.out.println("消息主体: " + new String(message.getBody()));
        System.out.println("应答码: " + replyCode);
        System.out.println("描述：" + replyText);
        System.out.println("消息使用的交换器 exchange : " + exchange);
        System.out.println("消息使用的路由键 routing : " + routingKey);
    }
}
