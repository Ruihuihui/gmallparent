package com.atguigu.gmall.product.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.model.product.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ManageService {




    List<BaseCategory1> getCategory1();


    List<BaseCategory2> getCategory2(Long category1Id);

    List<BaseCategory3> getCategory3(Long category2Id);

    /**
     *根据分类Id 获取平台属性数据
     * @param category1Id
     * @param category2Id
     * @param category3Id
     * @return
     */
    List<BaseAttrInfo> getBaseAttrInfoList(Long category1Id,Long category2Id ,Long category3Id);


    /**
     * 添加保存平台属性
     * @param baseAttrInfo
     */
    void saveAttrInfo(BaseAttrInfo baseAttrInfo);

    /**
     * 根据平台属性ID获取平台属性
     * @param attrId
     * @return
     */
    BaseAttrInfo getAttrInfo(Long attrId);


    /**
     * spu分页查询
     * @param pageParam
     * @param spuInfo
     * @return
     */
    IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam ,SpuInfo spuInfo);

    /**
     * 查询所有的销售属性数据
     * @return
     */
    List<BaseSaleAttr> getBaseSaleAttrList();

    /**
     * 保存spu sku数据
     * @param spuInfo
     * @return
     */
    void saveSpuInfo(SpuInfo spuInfo);


    /**
     * 根据spuId查询商品图片
     * @param spuId
     * @return
     */
    List<SpuImage> getSpuImageList(Long spuId);



    /**
     * 根据spuId 查询销售属性集合
     * @param spuId
     * @return
     */

    List<SpuSaleAttr> getSpuSaleAttrList(Long spuId);

    /**
     * 保存sku
     * @param skuInfo
     * @return
     */

    void saveSkuInfo(SkuInfo skuInfo);

    /**
     * SKU分页列表
     * @param size
     * @param limit
     * @return
     */
    IPage<SkuInfo> selectPage(IPage<SkuInfo> pageParam);

    /**
     * 商品上架
     * @param skuId
     * @return
     */
    void onSale(Long skuId);

    /**
     * 商品下架
     * @param skuId
     * @return
     */
    void cancelSale(Long skuId);

    /**  item   远程调用
     * 根据skuId 查询skuInfo信息
     * @param skuId
     * @return
     */
    SkuInfo getSkuInfo(Long skuId);

    /**  item   远程调用
     *根据3级分类的id查询分类信息
     * @param category3Id
     * @return
     */
    BaseCategoryView getCategoryViewByCategory3Id(Long category3Id);


    /**  item   远程调用
     * 单独查询商品价格
     * @param skuId
     * @return
     */
    BigDecimal getSkuPrice(Long skuId);

    /**  item   远程调用
     * 根据spuId，skuId 查询销售属性集合
     * @param skuId
     * @param spuId
     * @return
     */
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId);

    /**
     * 根据spuId 查询map 集合数据
     * @param spuId
     * @return
     */

    Map getSaleAttrValuesBySpu(Long spuId);

    /**
     * 商城首页分类展示
     * @return
     */
    List<JSONObject> getBaseCategoryList();



    /**
     * 通过品牌Id 来查询数据
     * @param tmId
     * @return
     */
    BaseTrademark getTrademarkByTmId(Long tmId);
    /**
     * 通过skuId 集合来查询数据
     * @param skuId
     * @return
     */
    List<BaseAttrInfo> getAttrList(Long skuId);

}

