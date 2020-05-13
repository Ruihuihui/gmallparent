package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @Override
    public void upperGoods(Long skuId) {
        //商家mysql -- > es
        //将实体类Goods 中的数据放入es中
        Goods goods = new Goods();
        //给Goods赋值
        //通过productFeignClient  先查询到skuInfo
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        //直接赋值
        goods.setId(skuInfo.getId());
        goods.setDefaultImg(skuInfo.getSkuDefaultImg());
        goods.setTitle(skuInfo.getSkuName());
        //goods.setPrice(skuInfo.getPrice().doubleValue());
        BigDecimal price = productFeignClient.getPrice(skuId);
        goods.setPrice(price.doubleValue());

        // 查询品牌
        BaseTrademark baseTrademark = productFeignClient.getTrademark(skuInfo.getTmId());
        if(baseTrademark!=null){
            goods.setTmId(skuInfo.getTmId());
            goods.setTmName(baseTrademark.getTmName());
            goods.setTmLogoUrl(baseTrademark.getLogoUrl());
        }

        BaseCategoryView baseCategoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());

        if(baseCategoryView!=null){
            goods.setCategory1Id(baseCategoryView.getCategory1Id());
            goods.setCategory1Name(baseCategoryView.getCategory1Name());
            goods.setCategory2Id(baseCategoryView.getCategory2Id());
            goods.setCategory2Name(baseCategoryView.getCategory2Name());
            goods.setCategory3Id(baseCategoryView.getCategory3Id());
            goods.setCategory3Name(baseCategoryView.getCategory3Name());
        }
        goods.setCreateTime(new Date());

        //查询sku对应的平台属性
        List<BaseAttrInfo> baseAttrInfoList = productFeignClient.getAttrList(skuId);
        if(baseAttrInfoList!=null&&baseAttrInfoList.size()>0){
            List<SearchAttr> searchAttrList =
                    baseAttrInfoList.stream().map(baseAttrInfo -> {
                        SearchAttr searchAttr = new SearchAttr();

                        searchAttr.setAttrId(baseAttrInfo.getId());
                        searchAttr.setAttrName(baseAttrInfo.getAttrName());
                        //一个sku只对应一个属性值
                        List<BaseAttrValue> baseAttrValueList = baseAttrInfo.getAttrValueList();

                        searchAttr.setAttrValue(baseAttrValueList.get(0).getValueName());
                        return searchAttr;
                    }).collect(Collectors.toList());
            goods.setAttrs(searchAttrList);
        }

        //添加到   elas
        goodsRepository.save(goods);
    }

    /**
     * 下架商品列表
     * @param skuId
     */
    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }

    /**
     * 更新热点
     * @param skuId
     */
    @Override
    public void incrHotScore(Long skuId) {
        // 定义key
        String hotKey ="hotScore";
        // 保存数据
        Double hotScore = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId" + skuId, 1);

        if(hotScore%10==0){
            Optional<Goods> optional = goodsRepository.findById(skuId);
            Goods goods = optional.get();
            goods.setHotScore(Math.round(hotScore));
            goodsRepository.save(goods);
        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        /**
         * 1，之多dsl语句
         * 2，执行dsl语句
         * 3，获取执行结果
         */
        SearchRequest searchRequest =  buildQueryDsl(searchParam);
        //引入操作es 的客户端类
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        //获取执行之后的数据
        SearchResponseVo responseVo = parseSearchResult(response);
        //设置分页相关的数据
        responseVo.setPageSize(searchParam.getPageSize());
        responseVo.setPageNo(searchParam.getPageNo());
        //设置和总条数可以从es中获取
        long totalPages = (responseVo.getTotal()+searchParam.getPageSize()-1)/searchParam.getPageSize();
        responseVo.setTotalPages(totalPages);
        return responseVo;
    }


   //获取执行之后的数据
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();

        //品牌数据通过聚合得到的
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        //获取品牌id
        ParsedLongTerms tmIdAgg = (ParsedLongTerms)aggregationMap.get("tmIdAgg");

        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            searchResponseTmVo.setTmId(Long.parseLong(((Terms.Bucket) bucket).getKeyAsString()));
            //获取品牌的名称
            Map<String, Aggregation> tmIdSubAggregationMap1 = bucket.getAggregations().asMap();
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdSubAggregationMap1.get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);
            //获取品牌的logo
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) tmIdSubAggregationMap1.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
            return searchResponseTmVo;
        }).collect(Collectors.toList());
        searchResponseVo.setTrademarkList(trademarkList);
        //获取平台属性数据
        ParsedNested attrAgg = (ParsedNested)aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if(buckets!= null && buckets.size()>0){
            List<SearchResponseAttrVo> searchResponseAttrVoList = buckets.stream().map(bucket -> {
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                searchResponseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());
                ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");

                searchResponseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueAggBuckets = attrValueAgg.getBuckets();
                List<String> valueList = valueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                searchResponseAttrVo.setAttrValueList(valueList);
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(searchResponseAttrVoList);
        }
        List<Goods> goodsList = new ArrayList<>();
        //获取商品数据
        SearchHits hits = response.getHits();
        SearchHit[] subHits = hits.getHits();
        System.out.println(subHits.length);
        System.out.println(subHits==null);
        if(null !=subHits && subHits.length>0){
            for (SearchHit subHit : subHits) {
                String goodsJson = subHit.getSourceAsString();
                Goods goods = JSONObject.parseObject(goodsJson,Goods.class);
                //从高亮中获取商品
                if(subHit.getHighlightFields().get("title")!=null){
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                goodsList.add(goods);

            }
        }
        searchResponseVo.setGoodsList(goodsList);
        //总记录数
        searchResponseVo.setTotal(hits.totalHits);
        return searchResponseVo;
    }

    //生成dsl语句
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        //查询器
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //声明一个QueryBuilders 对象  bool   Query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //判断查询关键字
        if(StringUtils.isNotEmpty(searchParam.getKeyword())){
            //创建QueryBuilders对象
            //MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("title",searchParam.getKeyword());
            //boolQueryBuilder.must(matchQueryBuilder);
            //title  小米  手机    分词查询
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.OR);
            boolQueryBuilder.must(title);
        }
        //设置品牌
        String trademark = searchParam.getTrademark();
        if(StringUtils.isNotEmpty(trademark)){
            //不为空说明按照品牌查询
            String[] split = StringUtils.split(trademark, ":");
           //判断分割之后的数据  split.length==2  是因为  key ：value
            if(split!=null&&split.length==2){
                //trem
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                boolQueryBuilder.filter(tmId);
            }
        }
        //设置分类id 过滤  1，2，3 级分类
        //trems  表示范围取值
        //trem 表示精确匹配
        if(null != searchParam.getCategory1Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id()));
        }
        if(null != searchParam.getCategory2Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id()));
        }
        if(null != searchParam.getCategory3Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id()));
        }
        //平台属性
        //prop =  23 :  4G :  运行内存
        String[] props = searchParam.getProps();
        if(null!=props && props.length>0){
            for (String prop : props) {
                String[] split = StringUtils.split(prop, ":");
                if(split!=null && split.length==3){
                    //构建查询语句
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    //匹配查询
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));
                    //将subBoolQuery 放入boolQuery
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));

                    //将boolQuery放入  总的查询器
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        //执行 Query方法
        searchSourceBuilder.query(boolQueryBuilder);
        //构建分页
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.size(searchParam.getPageSize());
        searchSourceBuilder.from(from);
        //按浏览量或价格 排序
        String order = searchParam.getOrder();
        if(StringUtils.isNotEmpty(order)){
            //进行数据分割
            String[] split = StringUtils.split(order, ":");
           //判断
            if(null != split && split.length==2){
                String filed = null;
                switch (split[0]){
                    case "1":
                        filed="hotScore";
                        break;
                    case "2":
                        filed="price";
                        break;
                }
                searchSourceBuilder.sort(filed,"asc".equals(split[1])? SortOrder.ASC:SortOrder.DESC);
            }else {
                searchSourceBuilder.sort("hotScore",SortOrder.DESC);
            }
        }
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        //设置高亮
        searchSourceBuilder.highlighter(highlightBuilder);

        //设置聚合   聚合品牌  和 品牌id
        TermsAggregationBuilder termsAggregationBuilder= AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));
        //将聚合的规则添加到查询器
        searchSourceBuilder.aggregation(termsAggregationBuilder);
        //平台属性
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))
                ));
        //设置查询时需要显示的字段
        searchSourceBuilder.fetchSource(new String[]{"id","dufaultImg","title","price"},null);
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        //打印dsl语句
        String query = searchSourceBuilder.toString();
        System.out.println("dsl:"+query);
        return searchRequest;
    }

    /* // 制作返回结果集
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();
//        private List<SearchResponseTmVo> trademarkList;
//        private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
//        private List<Goods> goodsList = new ArrayList<>();
//        private Long total;//总记录数
//        private Integer pageSize;//每页显示的内容
//        private Integer pageNo;//当前页面
//        private Long totalPages;

        // 品牌数据通过聚合得到的！
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        // 获取品牌Id Aggregation接口中并没有获取到桶的方法，所以在这进行转化
        // ParsedLongTerms 是他的实现。
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        // 从桶中获取数据
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            // 获取品牌的Id
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            searchResponseTmVo.setTmId(Long.parseLong(((Terms.Bucket) bucket).getKeyAsString()));

            // 获取品牌的名称
            Map<String, Aggregation> tmIdSubAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            // tmNameAgg 品牌名称的agg 品牌数据类型是String
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdSubAggregationMap.get("tmNameAgg");
            // 获取到品牌的名称并赋值
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);
            // 获取品牌的logo
            ParsedStringTerms tmlogoUrlAgg = (ParsedStringTerms) tmIdSubAggregationMap.get("tmLogoUrlAgg");
            String tmlogoUrl = tmlogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmlogoUrl);
            // 返回品牌
            return searchResponseTmVo;
        }).collect(Collectors.toList());
        // 赋值品牌数据
        searchResponseVo.setTrademarkList(trademarkList);

        // 获取平台属性数据 应该也是从聚合中获取
        // attrAgg 数据类型是nested ，转化一下
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        // 获取attrIdAgg 平台属性Id 数据
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        // 判断桶的集合不能为空
        if (null!=buckets && buckets.size()>0){
            // 循环遍历数据
            List<SearchResponseAttrVo> attrsList = buckets.stream().map(bucket -> {
                // 获取平台属性对象
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                searchResponseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());
                // 获取attrNameAgg 中的数据 名称数据类型是String
                ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                // 赋值平台属性的名称
                searchResponseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());

                // 赋值平台属性值集合 获取attrValueAgg
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueBuckets = attrValueAgg.getBuckets();
                // 获取该valueBuckets 中的数据
                // 将集合转化为map ，map的key 就是桶key，通过key获取里面的数据，并将数据变成一个list集合
                List<String> valueList = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                searchResponseAttrVo.setAttrValueList(valueList);
                // 返回平台属性对象
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(attrsList);
        }

        // 获取商品数据 goodsList
        // 声明一个存储商品的集合
        List<Goods> goodsList = new ArrayList<>();
        // 品牌数据需要从查询结果集中获取。
        SearchHits hits = response.getHits(); //  "hits" : {
        SearchHit[] subHits = hits.getHits(); //  "hits" : [ { ...} ]
        if (null!=subHits&& subHits.length>0){
            // 循环遍历数据
            for (SearchHit subHit : subHits) {
                // 获取商品的json 字符串
                String goodsJson = subHit.getSourceAsString();
                // 直接将json 字符串变成Goods.class
                Goods goods = JSONObject.parseObject(goodsJson, Goods.class);
                // 获取商品的时候，如果按照商品名称查询时，商品的名称显示的时候，应该高亮。但是，现在这个名称不是高亮
                // 从高亮中获取商品名称
                if (subHit.getHighlightFields().get("title")!=null){
                    // 说明当前用户查询是按照全文检索的方式查询的。
                    // 将高亮的商品名称赋值给goods
                    // [0] 因为高亮的时候，title 对应的只有一个值。
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                // 添加商品到集合
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);
        // 总记录数
        searchResponseVo.setTotal(hits.totalHits);
        return searchResponseVo;
    }

    */
    // 自动生成dsl 语句。
    /*  private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 查询器：{}
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 声明一个QueryBuilder 对象 query:bool
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 判断查询关键字
        if (StringUtils.isNotEmpty(searchParam.getKeyword())){
            // 创建QueryBuilder 对象
            // MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("title",searchParam.getKeyword());
            //  boolQueryBuilder.must(matchQueryBuilder);
            //  demo: 用户查询荣耀手机的时候， es 1. 分词 [荣耀  手机] Operator.AND 表示这个title 中这两个字段都必须存在
            //  如果 Operator.OR 那么 title 中有其中一个即可！
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            boolQueryBuilder.must(title);
        }
        // 设置品牌： trademark= 2:华为  2=tmId 华为=tmName
        String trademark = searchParam.getTrademark();
        if (StringUtils.isNotEmpty(trademark)){
            // 不为空说明用户按照品牌查询
            String[] split = StringUtils.split(trademark, ":");
            // 判断分割之后的数据格式
            // select * from basetrademark id = ?
            if (split!=null && split.length==2){
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                boolQueryBuilder.filter(tmId);
            }
        }
        // terms，term
        // terms:表示范围取值 select * from where id in (1,2,4)
        // term:表示精确取值 select * from where id = ?
        // 设置分类Id 过滤 通过一级分类Id，二级分类Id，三级分类Id
        if (null!=searchParam.getCategory1Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id",searchParam.getCategory1Id()));
        }
        if (null!=searchParam.getCategory2Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id",searchParam.getCategory2Id()));
        }

        if (null!=searchParam.getCategory3Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id",searchParam.getCategory3Id()));
        }
        // 平台属性
        // props=23:4G:运行内存
        //平台属性Id 平台属性值名称 平台属性名
        // nested 将平台属性，属性值作为独立的数据查询
        String[] props = searchParam.getProps();
        if (null!=props && props.length>0){
            // 循环遍历
            for (String prop : props) {
                // prop = 23:4G:运行内存
                String[] split = StringUtils.split(prop, ":");
                // split判断分割之后的格式 是否正确
                if (null!=split && split.length==3){
                    // 构建查询语句
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    // 匹配查询
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));

                    // 将subBoolQuery 放入boolQuery
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));
                    // 将boolQuery 放入总的查询器
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        // 执行query 方法
        searchSourceBuilder.query(boolQueryBuilder);
        // 构建分页
        // 开始条数
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        // 排序 1:hotScore 2:price
        String order = searchParam.getOrder();
        if (StringUtils.isNotEmpty(order)){
            // 进行分割数据
            String[] split = StringUtils.split(order, ":");
            // 判断 1:hotScore | 3 | price
            if (null!=split && split.length==2){
                // 设置排序规则
                // 定义一个排序字段
                String field = null;
                switch (split[0]){
                    case "1":
                        field="hotScore";
                        break;
                    case "2":
                        field="price";
                        break;
                }
                searchSourceBuilder.sort(field,"asc".equals(split[1])? SortOrder.ASC:SortOrder.DESC);
            }else {
                // 默认走根据热度进行降序排列。
                searchSourceBuilder.sort("hotScore",SortOrder.DESC);
            }
        }

        // 设置高亮
        // 声明一个高亮对象，然后设置高亮规则
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");// 商品的名称高亮
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlighter(highlightBuilder);

        // 设置聚合
        // 聚合品牌
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")  // 品牌Id
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName")) // 品牌名称
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));

        // 将聚合的规则添加到查询器
        searchSourceBuilder.aggregation(termsAggregationBuilder);
        // 平台属性
        // 设置nested 聚合。
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId") // 平台属性Id
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName")) // 平台属性名称
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        // 设置有效的数据，查询的时候哪些字段需要显示
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);

        //  GET /goods/info/_search
        // 设置索引库index，type
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        // 打印dsl 语句
        String query = searchSourceBuilder.toString();
        System.out.println("dsl:"+query);

        return searchRequest;
    }

   */
}
