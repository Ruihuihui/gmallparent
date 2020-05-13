package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BaseTrademarkService extends IService<BaseTrademark> {
    /**
     * 分页列表
     * @param param
     * @return
     */
    IPage<BaseTrademark> selectPage(Page<BaseTrademark> param);
    /**
     * spu 品牌属性查询显示
     * @return
     */
    List<BaseTrademark> getTrademarkList();

}
