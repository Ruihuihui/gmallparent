//package com.atguigu.gmall.mq.receiver;
//
//import com.rabbitmq.client.Channel;
//import lombok.SneakyThrows;
//import org.springframework.amqp.core.Message;
//import org.springframework.amqp.rabbit.annotation.Exchange;
//import org.springframework.amqp.rabbit.annotation.Queue;
//import org.springframework.amqp.rabbit.annotation.QueueBinding;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//
//@Component
//@Configuration
//public class ConfirmReceiver {
//
//    @SneakyThrows  //这个注解  忽略异常
//    @RabbitListener(bindings = @QueueBinding(
//            value = @Queue(value = "queue.confirm",autoDelete = "false"),
//            exchange = @Exchange(value = "exchange.confirm",autoDelete = "true"),
//            key = {"routing.confirm"}))
//    public void confirlMessage(Message message, Channel channel){
//        String str = new String(message.getBody());
//        System.out.println("接收到的消息"+str);
//
//        try {
//            //表示消息正确处理  手动签收了
//            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
//        } catch (IOException e) {
//            System.out.println("出现异常");
//            if(message.getMessageProperties().getRedelivered()){
//                System.out.println("消息已重复处理，拒绝签收");
//                //消息不重回队列
//                //false  表示是否重回队列
//                channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
//            }else {
//                //消息接收了 但没有正确处理
//                System.out.println("消息即将返会队列");
//                //第三个参数表示 如果消息没有正确处理  将再次返回
//                channel.basicNack(
//                        message.getMessageProperties().getDeliveryTag(),
//                        false,
//                        true);
//            }
//        }
//
//    }
//
//}
