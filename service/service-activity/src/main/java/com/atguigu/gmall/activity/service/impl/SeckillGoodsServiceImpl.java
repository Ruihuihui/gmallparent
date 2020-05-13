package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.CacheHelper;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService{
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    /**
     * 返回全部列表
     * @return
     */
    @Override
    public List<SeckillGoods> findAll() {
        //每天夜晚扫描发送消息  存入缓存 ，直接从缓存获取
        return redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
    }


    /**
     * 根据ID获取实体
     * @param skuId
     * @return
     */
    @Override
    public SeckillGoods getSeckillGoods(Long skuId) {
        return (SeckillGoods)redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
    }

    /**
     * 根据用户和商品ID实现秒杀下单
     * 预下单处理   那个用户 购买那个商品
     * @param skuId
     * @param userId
     */
    @Override
    public void seckillOrder(Long skuId, String userId) {
        //判断状态
        String status = (String) CacheHelper.get(skuId.toString());
        if("0".equals(status)){
            return;
        }
        //如何保证用户不能抢多次
        //用户第一次抢到了 。那么将抢到的商品信息存储到redis
        String userSeckillKey = RedisConst.SECKILL_USER+ userId;
        //返回为  true  说明第一次添加    setIfAbsent  redis  唯一性 不能覆盖 如果已经存在 直接返回false
        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(userSeckillKey, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        if(!aBoolean){
            return;
        }
        //用户可以下单  减少库存                                 rightPop()  突出一个商品
        //通过 key   seckill:stock:  skuId   value  减少要给缓存的秒杀库存
        String goodsId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if(StringUtils.isEmpty(goodsId)){
            //商品已售磐  通知其他兄弟节点  商品已卖完
            redisTemplate.convertAndSend("seckillpush",skuId+":0");
            return;
        }
        //记录订单  做一个秒杀的订单表
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(getSeckillGoods(skuId));
        orderRecode.setNum(1);
        orderRecode.setOrderStr(MD5.encrypt(userId));

        //将用户秒杀的订单放入缓存  key    seckill:orders    value   (  key   orderRecode.getUserId()  value    orderRecode)
        String orderSeckillKey = RedisConst.SECKILL_ORDERS;
        //用户的  key   seckill:orders     value     (key   orderRecode.getUserId(),   value   orderRecode)
        redisTemplate.boundHashOps(orderSeckillKey).put(orderRecode.getUserId(),orderRecode);
        //跟新数据库
        this.updateStockCount(orderRecode.getSeckillGoods().getSkuId());
    }
    /***
     * 根据商品id与用户ID查看订单信息
     * @param userId
     * @param skuId
     * @return
     */
    @Override
    public Result checkOrder(Long skuId, String userId) {
        //判断用户是否存在  不能购买两次
        //用户是否能够枪单
        Boolean aBoolean = redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        if(aBoolean){
            //判断订单是否存在
            Boolean aBoolean1 = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if(aBoolean1){
                //抢单成功  返回商品信息
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                //返回数据  用户枪单成功
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }
        //判断用户是否下过订单
        Boolean aBoolean1 = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        //判断用户已经下过订单
        if(aBoolean1){
            //获取用户id
            String orderId  = (String) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            //应该是第一次下单成功
            return Result.build(orderId,ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }
        String status = (String) CacheHelper.get(skuId.toString());
        if("0".equals(status)){
            //已卖完
            return Result.build(null,ResultCodeEnum.SECKILL_FINISH);
        }
        //默认情况下 排队中
        return Result.build(null,ResultCodeEnum.SECKILL_RUN);
    }

    //跟新数据库
    private void updateStockCount(Long skuId) {
        //更新库存，批量更新，用于页面显示，以实际扣减库存为准
        Long count = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();
        if(count%2==0){
            //商品卖完,同步数据库
            SeckillGoods seckillGoods = getSeckillGoods(skuId);
            //把缓存剩余的秒杀商品数量和 数据库同步
            seckillGoods.setStockCount(count.intValue());
            //更新数据库
            seckillGoodsMapper.updateById(seckillGoods);
            //更新缓存
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(),seckillGoods);
        }
    }
}
