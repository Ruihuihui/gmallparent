package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Component
public class SeckillReceiver {
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsService seckillGoodsService;
    //编写监听消息的方法 并将商品添加到缓存！
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importGoodsToRedis(Message message, Channel channel) throws IOException {
        /*
        获取秒杀商品 ，什么样的商品是秒杀商品
        秒杀商品的条件是审核状态为1 获取秒杀商品的开始时间、
        开始时间必须是当天
         查询审核状态1 并且库存数量大于0，当天的商品
        */
        QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
        seckillGoodsQueryWrapper.eq("status",1).
                eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        //seckillGoodsQueryWrapper.gt("stock_count",0);
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(seckillGoodsQueryWrapper);
        System.out.println(seckillGoodsList.toString());
        //有秒杀商品
        if(null!=seckillGoodsList&&seckillGoodsList.size()>0){
            //循环秒杀商品放入缓存
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                //再放入秒杀商品之前，先判断缓存是否已存在
                // 使用hash 数据类型保存商品
                Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGoods.getSkuId().toString());
                //判断当前商品是否有缓存
                if(flag){
                    // 当前商品已经在缓存中有了！ 所以不需要在放入缓存！
                    continue;
                }
                // 商品id为field ，对象为value 放入缓存  key = seckill:goods field = skuId value=商品字符串
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(),seckillGoods);
                //分析商品数量如何存储 ，如何防止商品超卖
                //使用redis 中list 的数据类型 --  单线程
                for (Integer i = 0; i < seckillGoods.getStockCount(); i++) {
                    //把每个要秒杀的商品id  通过  key  value  方式 保存到   redis 缓存
                                                                //  key   seckill:stock:+seckillGoods.getSkuId()   value  seckillGoods.getSkuId()
                    //通过循环  seckillGoods.getStockCount()  循环这个秒杀商品的数量  把全部数量通过   leftPush  循环放入缓存
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId()).leftPush(seckillGoods.getSkuId().toString());
                }
                //将所有商品状态初始化为1
                redisTemplate.convertAndSend("seckillpush",seckillGoods.getSkuId()+":1");
            }
            //手动确认消息已被处理
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }


    //监听用户发送过来的消息
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckillGoods(UserRecode recode,Message message,Channel channel) throws IOException {
        //判断用户信息不能为空
        if(null!=recode){
            //预下单处理   那个用户 购买那个商品
            seckillGoodsService.seckillOrder(recode.getSkuId(),recode.getUserId());
            //手动确认消息被处理
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }

    //定时删除任务
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void delGoodsToRedis(Message message, Channel channel) {
        QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
        //结束时间
        seckillGoodsQueryWrapper.eq("status", 1).le("end_time", new Date());
        //获取到结束的秒杀商品
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(seckillGoodsQueryWrapper);
        //删除缓存数据
        for (SeckillGoods seckillGoods : seckillGoodsList) {

            redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId());

        }
        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);

        //将状态更新为结束
        SeckillGoods seckillGoodsUp = new SeckillGoods();
        seckillGoodsUp.setStatus("2");
        seckillGoodsMapper.update(seckillGoodsUp, seckillGoodsQueryWrapper);
        // 手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }



}
