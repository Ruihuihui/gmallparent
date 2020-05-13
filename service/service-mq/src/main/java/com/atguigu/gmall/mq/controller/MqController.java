package com.atguigu.gmall.mq.controller;

import com.atguigu.gmall.common.result.Result;

import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;


@RestController
@Slf4j
@RequestMapping("/mq")
public class MqController {

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("sendConfirm")
    public Result sendConfirm(){
        String message = "Hello , RabbitMQ";
        rabbitService.sendMessage("exchange.confirm",
                "routing.confirm666", message);
        return Result.ok();
    }

    @GetMapping("sendDeadLettle")
    public Result sendDeadLettle(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitTemplate.convertAndSend(DeadLetterMqConfig.exchange_dead,
                DeadLetterMqConfig.routing_dead_1, "hello");
        System.out.println(sdf.format(new Date())+"Delay sent");
        return Result.ok();
    }

    @GetMapping("sendDelay")
    public Result sendDelay() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitTemplate.convertAndSend(DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay,
                sdf.format(new Date()));
        System.out.println(sdf.format(new Date()) + " Delay sent.");
        return Result.ok();
    }

}

