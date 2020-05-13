package com.atguigu.gmall.all.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class PassportController {

    @GetMapping("login.html")
    public String login(HttpServletRequest request){
        //从哪里点击的登录  应该跳回那里去
        String originUrl = request.getParameter("originUrl");
        //前台需要跳转回去 所以我这里保存
        request.setAttribute("originUrl",originUrl);
        return "login";
    }
}
