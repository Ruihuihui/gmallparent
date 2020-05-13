package com.atguigu.gmall.item.service;

import java.util.Map;

public interface ItemService {
    //商品详情页面要想获取数据，必须有一个skuId

    //商品详情页面是从list上个页面过来的

    /**
     * map.put(price ,商品的价格)
     * map.put( skuInfo skuInfo数据)
     * @param skuId
     * @return
     */
    Map<String,Object> getBySkuId(Long skuId);
}
