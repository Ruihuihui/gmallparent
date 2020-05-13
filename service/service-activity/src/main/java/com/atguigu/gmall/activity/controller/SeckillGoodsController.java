package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.CacheHelper;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsController {
    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RabbitService rabbitService;
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private OrderFeignClient orderFeignClient;


    /**
     * 返回全部列表
     *
     * @return
     */
    @GetMapping("/findAll")
    public Result findAll() {
        return Result.ok(seckillGoodsService.findAll());
    }

    /**
     * 获取实体
     *
     * @param skuId
     * @return
     */
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable("skuId") Long skuId) {
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    /**
     * 获取下单码
     * @param skuId
     * @return
     */
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable Long skuId, HttpServletRequest request){
        //获取用户id
        String userId = AuthContextHolder.getUserId(request);
        //用户要秒杀的商品   seckillGoods
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        if(null!=seckillGoods){
            //获取下单码
            Date date = new Date();
            //获取下单码  必须在活动开始之后 结束之前
            if(DateUtil.dateCompare(seckillGoods.getStartTime(),date) && DateUtil.dateCompare(date,seckillGoods.getEndTime())){
                //符合条件生成下单码
                String skuIdStr = MD5.encrypt(userId);
                //保存 skuIdStr  返回给页面使用
                return Result.ok(skuIdStr);
            }
        }
        return Result.fail().message("获取下单码失败！");
    }

    /**
     * 根据用户和商品ID实现秒杀下单
     *
     * @param skuId
     * @return
     */
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable Long skuId,
                               HttpServletRequest request){
        //获取页面提交过来的下单码
        String skuIdStr = request.getParameter("skuIdStr");
        String userId = AuthContextHolder.getUserId(request);
        String encrypt = MD5.encrypt(userId);
        if(!skuIdStr.equals(encrypt)){
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        //获取状态位
        String stutas = (String)CacheHelper.get(skuId.toString());
        if(StringUtils.isEmpty(stutas)){
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        //可以抢购
        if("1".equals(stutas)){
            //记录当前谁在抢购  那个用户
            UserRecode userRecode = new UserRecode();
            //用户id
            userRecode.setUserId(userId);
            //秒杀商品的id
            userRecode.setSkuId(skuId);
            //将用户放入队列中进行排队
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER,MqConst.ROUTING_SECKILL_USER,userRecode);
        }else {
            //说明商品已经售完
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }
        return Result.ok();
    }
    /**
     * 查询秒杀状态
     * @return
     */
    @GetMapping(value = "auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable Long skuId,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        return seckillGoodsService.checkOrder(skuId,userId);
    }

    /**
     * 秒杀确认订单   下订单页面 详细信息获取
     * @param request
     * @return
     */
    @GetMapping("/auth/trade")
    Result<Object> trade(HttpServletRequest request){
        //收货人  送货清单 总金额 等  并保存到数据库

        // 获取用户id
        String userId = AuthContextHolder.getUserId(request);
        //获取收获地址列表
        List<UserAddress> userAddressListByUserId = userFeignClient.findUserAddressListByUserId(userId);
        //获取用户购买的商品
        OrderRecode orderRecode = (OrderRecode)redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if(null==orderRecode){
            return Result.fail().message("非法操作");
        }
        //获取用户购买的商品
        // 声明一个集合来存储订单明细
        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();
        List<OrderDetail> orderDetailList = new ArrayList<>();
        //给订单明细赋值
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        orderDetail.setSkuNum(orderRecode.getNum());
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());
        orderDetailList.add(orderDetail);
        //计算当前总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        orderInfo.sumTotalAmount();
        //声明一个map集合
        HashMap<String, Object> map = new HashMap<>();
        //获取收获地址列表
        map.put("userAddressList", userAddressListByUserId);
        // 订单明细
        map.put("detailArrayList", orderDetailList);
        // 保存总金额
        map.put("totalAmount", orderInfo.getTotalAmount());

        return Result.ok(map);
    }


    /**
     * 秒杀提交订单
     *
     * @param orderInfo
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);

        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if(null==orderRecode){
            return Result.fail().message("非法操作");
        }
        //提交订单的操作
        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if(null==orderId){
            return Result.fail().message("非法操作");
        }
        //删除下单操作
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);

        //将用户下单记录保存上
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId,orderId.toString());
        return Result.ok(orderId);
    }
}
