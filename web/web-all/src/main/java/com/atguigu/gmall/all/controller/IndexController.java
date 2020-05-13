package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.servlet.http.HttpServletRequest;
import java.io.FileWriter;
import java.io.IOException;

@Controller
public class IndexController {
    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private SpringTemplateEngine springTemplateEngine;


    @GetMapping("createHtml")
    @ResponseBody
    public Result createHtml() throws IOException {
        Result result = productFeignClient.getBaseCategoryList();
        Context context = new Context();
        context.setVariable("list",result.getData());
        FileWriter fileWriter = new FileWriter("E:\\index.html");
        springTemplateEngine.process("index/index.html",context,fileWriter);
        return Result.ok();
    }

//    @GetMapping({"/","index.html"})
//    public String index(){
//        return "index";
//    }
    @GetMapping({"/","index.html"})
    public String index(HttpServletRequest request){
        Result result = productFeignClient.getBaseCategoryList();
        request.setAttribute("list",result.getData());
        return "index/index";
    }
}
