package com.atguigu.gmall.product.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 *这个类中的所有数据是给外部提供数据使用的
 */
@Api(tags = "商品基础属性查询接口")
@RestController
@RequestMapping("api/product")
public class ProductApiController {
    @Autowired
    private ManageService manageService;

    /**   item   远程调用
     * 根据skuId获取sku信息
     * @param skuId
     * @return
     */
    @ApiOperation(value = "根据skuId获取sku信息")
    @GetMapping("inner/getSkuInfo/{skuId}")
    public SkuInfo getSkuInfo(@PathVariable("skuId") Long skuId){
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        return skuInfo;
    }
    /**  item   远程调用
     *根据3级分类的id查询分类信息
     * @param category3Id
     * @return
     */
    @ApiOperation(value = "根据3级分类的id查询分类信息")
    @GetMapping("inner/getCategoryView/{category3Id}")
    public BaseCategoryView getCategoryView(@PathVariable("category3Id") Long category3Id){
        BaseCategoryView categoryViewByCategory3Id = manageService.getCategoryViewByCategory3Id(category3Id);
        return categoryViewByCategory3Id;
    }

    /**  item   远程调用
     * 单独查询商品价格
     * @param skuId
     * @return
     */
    @ApiOperation(value = "单独查询商品价格")
    @GetMapping("inner/getPrice/{skuId}")
    public BigDecimal getPrice(@PathVariable("skuId") Long skuId){
        return manageService.getSkuPrice(skuId);
    }

    /** item   远程调用
     * 根据spuId，skuId 查询销售属性集合
     * @param skuId
     * @param spuId
     * @return
     */
    @ApiOperation(value = "根据spuId，skuId 查询销售属性集合")
    @GetMapping("inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(@PathVariable("skuId") Long skuId,
                                                          @PathVariable("spuId") Long spuId){
        return manageService.getSpuSaleAttrListCheckBySku(skuId,spuId);
    }


    /**
     * 根据spuId 查询map 集合属性
     * @param spuId
     * @return
     */
    @ApiOperation(value = "根据spuId 查询map 集合属性")
    @GetMapping("inner/getSkuValueIdsMap/{spuId}")
    public Map getSkuValueIdsMap(@PathVariable("spuId") Long spuId){
        return manageService.getSaleAttrValuesBySpu(spuId);
    }

    /**
     * 获取首页所有的分类数据
     * @return
     */
    @GetMapping("getBaseCategoryList")
    public Result getBaseCategoryList(){
        List<JSONObject> baseCategoryList = manageService.getBaseCategoryList();
        return Result.ok(baseCategoryList);
    }
    /**   service-list    服务远程调用
     * 通过品牌Id 来查询数据
     * @param tmId
     * @return
     */
    @GetMapping("inner/getTrademark/{tmId}")
    public BaseTrademark getTrademark(@PathVariable("tmId") Long tmId){
        return manageService.getTrademarkByTmId(tmId);
    }
    /**    service-list    服务远程调用
     * 通过skuId 集合来查询数据
     * @param skuId
     * @return
     */
    @GetMapping("inner/getAttrList/{skuId}")
    public List<BaseAttrInfo> getAttrList(@PathVariable("skuId") Long skuId){
        return manageService.getAttrList(skuId);
    }

}
