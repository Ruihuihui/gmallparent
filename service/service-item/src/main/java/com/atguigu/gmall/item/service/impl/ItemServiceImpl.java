package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private ListFeignClient listFeignClient;
   @Override
    public Map<String, Object> getBySkuId(Long skuId) {
       // 这个地方没有获取到数据，所以你保存的时候，都是null
       // 解决问题：思路：
       /*
       1.   先看productFeignClient.getSkuInfo(skuId); 远程为什么没有得到数据。
            也就是直接看service-product 项目中的数据接口。
        */
       //商品详情页面要想获取数据，必须有一个skuId

       //商品详情页面是从list上个页面过来的

       /**
        * map.put(price ,商品的价格)
        * map.put( skuInfo skuInfo数据)
        * @param skuId
        * @return
        */
        Map<String,Object> result = new HashMap<>();
        // 通过skuId 查询skuInfo
       CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
           SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
           // 保存skuInfo
           result.put("skuInfo", skuInfo);
           return skuInfo;
       }, threadPoolExecutor);
       // 销售属性-销售属性值回显并锁定
       CompletableFuture<Void> spuSaleAttrCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
           List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getId(), skuInfo.getSpuId());
           // 保存数据
           result.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
       }, threadPoolExecutor);

       //根据spuId 查询map 集合属性
       CompletableFuture<Void>valuesSkuJsonoCmpletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
           Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());

           //json转换
           String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
           //  // 保存valuesSkuJson
           result.put("valuesSkuJson", valuesSkuJson);
       }, threadPoolExecutor);
       //获取商品最新价格
       CompletableFuture<Void> priceCompletableFuture = CompletableFuture.runAsync(() -> {

           BigDecimal price = productFeignClient.getPrice(skuId);
           // 获取价格
           result.put("price", price);
       }, threadPoolExecutor);


       //获取商品分类
       CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
           BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
           //保存商品分类数据
           result.put("categoryView", categoryView);
       }, threadPoolExecutor);
       CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
           listFeignClient.incrHotScore(skuId);
       }, threadPoolExecutor);

       CompletableFuture.allOf(skuInfoCompletableFuture,
                spuSaleAttrCompletableFuture,
                valuesSkuJsonoCmpletableFuture,
                priceCompletableFuture,
                categoryViewCompletableFuture,
               incrHotScoreCompletableFuture).join();

       return result;
    }
}
