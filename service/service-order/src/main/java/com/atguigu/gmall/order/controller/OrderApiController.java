package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.spring.web.json.Json;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/order")
public class OrderApiController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;
    @Autowired
    private ProductFeignClient productFeignClient;
    /**
     * 获取用户地址列表
     * @return
     */

    @GetMapping("/auth/trade")
    public Result trade(HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        //获取用户的地址列表
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        //获取送货清单
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        int totalNum  = 0;
        ArrayList<OrderDetail> orderDetailList = new ArrayList<>();
        for (CartInfo cartInfo : cartCheckedList) {
            OrderDetail orderDetail = new OrderDetail();

            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());
            totalNum += cartInfo.getSkuNum();
            //把每个订单明细添加进去
            orderDetailList.add(orderDetail);
        }
        //算出当前订单总金额
        OrderInfo orderInfo = new OrderInfo();
        //将订单明细赋值给 orderInfo
        orderInfo.setOrderDetailList(orderDetailList);
        //计算总金额
        orderInfo.sumTotalAmount();
        //将数据封装到map
        HashMap<String, Object> map = new HashMap<>();
        //保存 userAddressList
        map.put("userAddressList",userAddressList);
        //保存所有订单明细
        map.put("detailArrayList",orderDetailList);
        //保存总数量
        //map.put("totalNum", orderDetailList.size());
        map.put("totalNum", totalNum);
        //保存总金额
        map.put("totalAmount", orderInfo.getTotalAmount());
        //生成一个流水号 给页面使用
        String tradeNo = orderService.getTradeNo(userId);
        map.put("tradeNo",tradeNo);
        return Result.ok(map);
    }


    /**
     * 下订单
     * @param orderInfo
     * @param request
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo,
                              HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        //在保存之前将用户id赋值给订单  orderInfo
        orderInfo.setUserId(Long.parseLong(userId));
        //下订单时查询页面流水号
        String tradeNo = request.getParameter("tradeNo");
        //页面流水号和缓存流水号作比较
        boolean flag = orderService.chechTradeNo(tradeNo, userId);
        if (!flag) {
            return Result.fail().message("不能重复提交订单！");
        }
        //缓存存在流水号 上门校验成功
        //删除流水号 并进行下订单操作  以访重复下订单
        orderService.deleteTradeNo(userId);
        //验证每个商品的库存是否足够
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if(null!=orderDetailList&&orderDetailList.size()>0){
            for (OrderDetail orderDetail : orderDetailList) {
                //返回true  表示又足够的库存
                boolean result = orderService.chechStock(orderDetail.getSkuId(),orderDetail.getSkuNum());
                if(!result){
                    return Result.fail().message(orderDetail.getSkuName()+"库存不足");
                }

                //下订单时验证价格
                BigDecimal price = productFeignClient.getPrice(orderDetail.getSkuId());
                if(orderDetail.getOrderPrice().compareTo(price)!=0){
                    //更新购物车商品的价格
                    cartFeignClient.loadCartCache(userId);
                    return Result.fail().message(orderDetail.getSkuName()+"商品价格有变动");
                }
            }
        }

        Long orderId = orderService.saveOrderInfo(orderInfo);
        return Result.ok(orderId);
    }


    /**
     * 内部调用获取订单
     * 根据订单Id 查询订单信息
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId){
        return orderService.getOrderInfo(orderId);
    }

    /**
     * 拆单
     */
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        //获取传递过来的参数
        String orderId = request.getParameter("orderId");
        String wareSkuMap = request.getParameter("wareSkuMap");
        //获取子订单集合
        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId),wareSkuMap);
        List<Map> mapArrayList = new ArrayList<>();
        for (OrderInfo orderInfo : subOrderInfoList) {
            //将子订单中的数据变换成一个个map
            Map map = orderService.initWareOrder(orderInfo);
            mapArrayList.add(map);
        }
        //返回子订单的集合字符串
        return JSON.toJSONString(mapArrayList);
    }


    //保存订单数据
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo){
        Long aLong = orderService.saveOrderInfo(orderInfo);
        return aLong;
    }
}
