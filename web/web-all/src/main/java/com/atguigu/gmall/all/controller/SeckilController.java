package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class SeckilController {

    @Autowired
    private ActivityFeignClient activityFeignClient;

    /**
     * 秒杀列表
     * @param model
     * @return
     */
    @GetMapping("seckill.html")
    public String index(Model model) {
        Result result = activityFeignClient.findAll();
        model.addAttribute("list", result.getData());
        return "seckill/index";
    }

    /**
     * 秒杀详情
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping("seckill/{skuId}.html")
    public String getItem(@PathVariable Long skuId, Model model){
        // 通过skuId 查询skuInfo
        Result result = activityFeignClient.getSeckillGoods(skuId);
        model.addAttribute("item", result.getData());
        //返回商品详情页面
        return "seckill/item";
    }


    /**
     * 秒杀排队
     * @param skuId
     * @param skuIdStr
     * @param request
     * @return
     */
    @GetMapping("seckill/queue.html")
    public String queue(@RequestParam(name = "skuId") Long skuId,
                        @RequestParam(name = "skuIdStr") String skuIdStr,
                        HttpServletRequest request){
        request.setAttribute("skuId", skuId);
        request.setAttribute("skuIdStr", skuIdStr);
        return "seckill/queue";
    }

    @GetMapping("auth/trade")
    public String trade(Model model){
        //获取到下单数据
        Result<Map<String,Object>> result = activityFeignClient.trade();
        if(result.isOk()){
            model.addAllAttributes(result.getData());
            return "seckill/trade";
        }else {
            model.addAttribute("message",result.getMessage());

            return "seckill/fail";
        }
    }

}
