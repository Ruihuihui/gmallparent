package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.MalformedParameterizedTypeException;
import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${ware.url}")
    private String WARE_URL;

    @Autowired
    private RabbitService rabbitService;

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    @Override
    @Transactional
    public Long saveOrderInfo(OrderInfo orderInfo) {

        //保存 orderInfo
        //缺少 总金额  订单状态  第三方交易编号  创建订单时间  订单过期时间  订单状态
        orderInfo.sumTotalAmount();
        //订单状态
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        //第三方交易编号
        String outTradeNo = "ATGUIGU"+System.currentTimeMillis()+new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        //订单创建时间
        orderInfo.setCreateTime(new Date());
        //订单过期时间
        //先获取日历
        Calendar calendar = Calendar.getInstance();
        //在日历的基础上加1天
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        //进程状态  预订单有个绑定关系
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuilder stringBuilder = new StringBuilder();
        for (OrderDetail orderDetail : orderDetailList) {
            stringBuilder.append(orderDetail.getSkuNum()+"");
        }
        //长度处理
        if(stringBuilder.length()>100){
            orderInfo.setTradeBody(stringBuilder.toString().substring(0,100));
        }else {
            orderInfo.setTradeBody(stringBuilder.toString());
        }
        //订单的主题描述
        orderInfoMapper.insert(orderInfo);
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insert(orderDetail);
        }
        //延时取消订单
        rabbitService.sendDelayMessage(
                MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,
                MqConst.ROUTING_ORDER_CANCEL,
                orderInfo.getId(),
                MqConst.DELAY_TIME);
        return orderInfo.getId();
    }

    /**
     * 生成流水号 同时放入缓存
     * @param userId
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        //获取流水号
        String tradeNo = UUID.randomUUID().toString().replace("-","");
       //将流水号放入缓存
        String tradeNoKey = getTradeNoKey(userId);
        //放入缓存
        redisTemplate.opsForValue().set(tradeNoKey,tradeNo);
        //返回给页面流水号
        return tradeNo;
    }

    private String getTradeNoKey(String userId) {
        return "user:"+userId+":tradeCode";
    }

    /**
     * 比较流水号
     * @param tradeNo  页面的流水号
     * @param userId  //获取缓存的流水号
     * @return
     */
    @Override
    public boolean chechTradeNo(String tradeNo, String userId) {
        String tradeNoKey = getTradeNoKey(userId);
        String redisTradeNo = (String)redisTemplate.opsForValue().get(tradeNoKey);

        return tradeNo.equals(redisTradeNo);
    }



    /**
     * 删除缓存的流水号
     * @param userId
     */
    @Override
    public void deleteTradeNo(String userId) {
        String tradeNoKey = getTradeNoKey(userId);
        redisTemplate.delete(tradeNoKey);
    }

    /**
     * 判断库存是否足够
     * @param skuId
     * @param skuNum
     * @return
     */
    @Override
    public boolean chechStock(Long skuId, Integer skuNum) {
        //0表示没有库存
        String result = HttpClientUtil.doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);

        return "1".equals(result);
    }


    /**
     * 根据订单Id 查询订单信息
     * @param orderId
     * @return
     */
    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        QueryWrapper<OrderDetail> orderDetailQueryWrapper = new QueryWrapper<>();
        orderDetailQueryWrapper.eq("order_id",orderId);
        List<OrderDetail> orderDetails = orderDetailMapper.selectList(orderDetailQueryWrapper);
        orderInfo.setOrderDetailList(orderDetails);

        return orderInfo;
    }

    /**
     * 根据订单id关闭过期订单
     * @param orderId
     */
    @Override
    public void execExpiredOrder(Long orderId) {
        //关闭订单
        updateOrderStatus(orderId, ProcessStatus.CLOSED);
        //发送  rabbit  消息关闭支付宝订单
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,
                MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        
    }

    /**
     * 更改订单状态
     * @param orderId
     * @param processStatus
     */
    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        //赋值更新的内容
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }
    // 发送消息，通知仓库改库存
    @Override
    public void sendOrderStatus(Long orderId) {
        //更改订单状态
        this.updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);

        String wareJson = initWareOrder(orderId);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK, MqConst.ROUTING_WARE_STOCK, wareJson);
    }

    public String initWareOrder(Long orderId) {
        OrderInfo orderInfo = getOrderInfo(orderId);
        Map map = initWareOrder(orderInfo);
        return JSON.toJSONString(map);
    }

    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！
          /*
            details:[{skuId:101,skuNum:1,skuName:
            ’小米手64G’},
            {skuId:201,skuNum:1,skuName:’索尼耳机’}]
             */
        List<Map> mapArrayList = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId", orderDetail.getSkuId());
            orderDetailMap.put("skuNum", orderDetail.getSkuNum());
            orderDetailMap.put("skuName", orderDetail.getSkuName());
            mapArrayList.add(orderDetailMap);
        }
        map.put("details", mapArrayList);

        return map;
    }

    /**
     * 拆单
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    public List<OrderInfo> orderSplit(long orderId, String wareSkuMap) {
        //先获取原始订单
        //将获取到的订单 不同的仓库拆分

        //创建一个新的子订单
        //给子订单赋值

        //保存子订单
        //酱紫订单添加到集合
        //修改订单的状态
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        OrderInfo orderInfo = getOrderInfo(orderId);
        List<Map> mapList = JSON.parseArray(wareSkuMap, Map.class);
        if(null!=mapList&&mapList.size()>0){
            for (Map map : mapList) {
                //获取仓库id
                String wareId = (String)map.get("wareId");
                //获取skuIds集合
                List<String> skuIds = (List<String>)map.get("skuIds");
                OrderInfo subOrderInfo = new OrderInfo();
                //通过属性拷贝  主键自增
                BeanUtils.copyProperties(orderInfo,subOrderInfo);
                //拷贝的时候注意id  主键自增
                subOrderInfo.setId(null);
                //给原始订单id
                subOrderInfo.setParentOrderId(orderInfo.getId());
                //赋值仓库id
                subOrderInfo.setWareId(wareId);
                List<OrderDetail> orderDetailLists = new ArrayList<>();
                //赋值此订单的明细表
                List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
                for (OrderDetail orderDetail : orderDetailList) {
                    //对比条件 skuId
                    for (String skuId : skuIds) {
                        if(Long.parseLong(skuId) == orderDetail.getSkuId().intValue()){
                            //相等 就可以保存
                            orderDetailLists.add(orderDetail);
                        }
                    }
                }
                //将子订单明细赋值给 子订单
                subOrderInfo.setOrderDetailList(orderDetailLists);
                //计算子订单的总金额
                subOrderInfo.sumTotalAmount();
                //保存订单
                this.saveOrderInfo(subOrderInfo);
                //添加子订单到集合
                subOrderInfoList.add(subOrderInfo);
            }
        }
        //修改订单状态
        updateOrderStatus(orderId,ProcessStatus.SPLIT);

        return subOrderInfoList;
    }

    /**
     * 关闭过期订单
     * @param orderId
     * @param flag
     */
    @Override
    public void execExpiredOrder(Long orderId, String flag) {
        //flag 为1 的时候之关闭orderInfo
        this.updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if("2".equals(flag)){
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_PAY,orderId);
        }
    }
}
