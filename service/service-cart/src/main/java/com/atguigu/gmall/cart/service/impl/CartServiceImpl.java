package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private RedisTemplate redisTemplate;
    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        /**
         * 添加购物车  判断购物车是否有该商品
         * true  数量相加
         * false  直接添加
         *
         * 特殊处理   添加购物车的时候 直接将购物放到缓存中
         */
        //先判断是个否有缓存
        String careKey = getCartKey(userId);
        if(!redisTemplate.hasKey(careKey)){
            //查询数据并添加到缓存
            loadCartCache(userId);
        }



        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        //看数据中购物车是个否有已经添加的商品
        cartInfoQueryWrapper.eq("sku_id",skuId).eq("user_id",userId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfoQueryWrapper);
        //生成购物车的cartKey
        String cartKey = getCartKey(userId);
        //数据库已存在商品时
        if(null!=cartInfoExist){
            //说明购物车已经添加过当前商品那么数量加    要添加的数量  skuNum
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            //获取商品实时价格
            BigDecimal price = productFeignClient.getPrice(skuId);
            cartInfoExist.setSkuPrice(price);
            //更新数据库
            cartInfoMapper.updateById(cartInfoExist);

        }else { //购物车没有商品
            CartInfo cartInfo = new CartInfo();
            //给cartInfo  赋值
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfoMapper.insert(cartInfo);

            cartInfoExist = cartInfo;
        }
        //添加到缓存 ，添加成功后直接走缓存了  如果缓存过期了  才会走数据库
        //使用hash  数据类型 hset

        redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);

        //购物车在缓存中的过期时间
        setCartKeyExpire(cartKey);

    }
    /**
     * 通过用户Id 查询购物车列表
     * @param userId
     * @param userTempId
     * @return
     */

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        //判断用户是否登录  未登录
        if(StringUtils.isEmpty(userId)){
            //获取临时购物车的数据
            cartInfoList = getCartList(userTempId);
            return cartInfoList;
        }
        //登录
        if(!StringUtils.isEmpty(userId)){
            //获取未登录的购物车数据
            List<CartInfo> cartUserTempList = getCartList(userTempId);
            if(!CollectionUtils.isEmpty(cartUserTempList)){
                //登录加未登录
               cartInfoList = mergeToCareList(cartUserTempList,userId);

               //合并之后删除未登录购物车的数据
                deleteCartLsit(userTempId);
            }
            //如果未登录的购物车没有数据  或者userTempId为空
            if(CollectionUtils.isEmpty(cartUserTempList)||StringUtils.isEmpty(userTempId)){
                cartInfoList = getCartList(userId);
            }

            return cartInfoList;
        }

        return cartInfoList;
    }

    /**
     * 更新选中状态
     *
     * @param userId
     * @param isChecked
     * @param skuId
     */
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        //更新的状态
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        //更新的条件
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id",userId).eq("sku_id",skuId);
        cartInfoMapper.update(cartInfo,cartInfoQueryWrapper);

        //更新缓存
        String cartKey = getCartKey(userId);
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        if(boundHashOperations.hasKey(skuId.toString())){
            CartInfo cartInfoUpd  = (CartInfo) boundHashOperations.get(skuId.toString());
            //更改缓存状态
            cartInfoUpd.setIsChecked(isChecked);
            //put 上去
            boundHashOperations.put(skuId.toString(),cartInfoUpd);
            //更新缓存的过期时间
            this.setCartKeyExpire(cartKey);
        }
    }

    /**
     * 删除购物车
     * @param skuId
     * @param
     * @return
     */
    @Override
    public void deleteCart(Long skuId, String userId) {
        String cartKey = getCartKey(userId);
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        //判断缓存中是否有商品Id
        if(boundHashOperations.hasKey(skuId.toString())){
            //如果有这个key  则删除
            boundHashOperations.delete(skuId.toString());
        }
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userId).eq("sku_id",skuId));
    }

    /**
     * 已选中的  购物清单
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        //获取缓存
        String cartKey = getCartKey(userId);
        List<CartInfo> cartCacheList = redisTemplate.opsForHash().values(cartKey);
        if(null!=cartCacheList&&cartCacheList.size()>0){
            for (CartInfo cartInfo : cartCacheList) {
                //循环每个  的  IsChecked == 1  就添加到已选中的购物清单
                if(cartInfo.getIsChecked().intValue()==1){
                    cartInfoList.add(cartInfo);
                }
            }
        }
        return cartInfoList;
    }

    //合并之后删除未登录购物车的数据
    private void deleteCartLsit(String userTempId) {
        //先判度是否有未登录缓存
        String cartKey = getCartKey(userTempId);
        Boolean aBoolean = redisTemplate.hasKey(cartKey);
        if(aBoolean){
            redisTemplate.delete(cartKey);
        }

        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userTempId));
    }

    //登录加未登录购物车合并
    private List<CartInfo> mergeToCareList(List<CartInfo> cartUserTempList, String userId) {
        List<CartInfo> cartLoginList = getCartList(userId);
        //便是以shuId 为key  以cartInfo 为value 一个map集合
        Map<Long, CartInfo> cartInfoMapLogin = cartLoginList.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));
        //循环未登录购物车数据
        for (CartInfo cartInfo : cartUserTempList) {
            //取出未登录的商品id
            Long skuId = cartInfo.getSkuId();
            //看登陆购物车是否有未登录的skuid
            if(cartInfoMapLogin.containsKey(skuId)){
                //获取登录数据
                CartInfo cartInfoLogin = cartInfoMapLogin.get(skuId);
                //商品数量相加
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum()+cartInfo.getSkuNum());
                //合并时是否有商品被选中
                //未登录显示商品以选中
                if(cartInfoLogin.getIsChecked().intValue()==1){
                    cartInfoLogin.setIsChecked(1);
                }
                cartInfoMapper.updateById(cartInfoLogin);
            }else {
                //未登录的临时id  改变成已登录的用户id
                cartInfo.setUserId(userId);
                cartInfoMapper.insert(cartInfo);
            }
        }
        //将合并之后的数据查询出来
        List<CartInfo> cartInfoList = loadCartCache(userId);
        return cartInfoList;
    }

    //获取临时购物车的数据   也可以获取登录购物车的数据
    private List<CartInfo> getCartList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        if(StringUtils.isEmpty(userId)){
            return cartInfoList;
        }
        //购物车的  key
        String cartKey = getCartKey(userId);
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if(null!=cartInfoList&&cartInfoList.size()>0){
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    return o1.getId().compareTo(o2.getId());
                }
            });
            return cartInfoList;
        }else {
            cartInfoList = loadCartCache(userId);
            return cartInfoList;
        }
    }
    //从数据库中获取购物车数据  并添加到缓存
    public List<CartInfo> loadCartCache(String userId) {

        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id",userId);
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(cartInfoQueryWrapper);
        //数据库为空直接返回
        if(CollectionUtils.isEmpty(cartInfoList)){
            return cartInfoList;
        }

        //数据库中有数据
        HashMap<String, CartInfo> hashMap = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            //查询最新价格
            cartInfo.setSkuPrice(productFeignClient.getPrice(cartInfo.getSkuId()));
            hashMap.put(cartInfo.getSkuId().toString(),cartInfo);

        }
        //再次将map放入缓存
        //获取缓存key
        String cartKey = getCartKey(userId);
        redisTemplate.opsForHash().putAll(cartKey,hashMap);
        //设置缓存过期时间
        setCartKeyExpire(cartKey);

        return cartInfoList;
    }

    //购物车在缓存中的过期时间
    private Boolean setCartKeyExpire(String cartKey) {
        return redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    //获取购物车的cartKey
    private  String getCartKey(String userId){
        //区分是谁的购物车
        return RedisConst.USER_KEY_PREFIX+userId+RedisConst.USER_CART_KEY_SUFFIX;
    }
}
