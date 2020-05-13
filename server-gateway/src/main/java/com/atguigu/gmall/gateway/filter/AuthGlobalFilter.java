package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关过滤器
 */
@Component
public class AuthGlobalFilter  implements GlobalFilter {
    //获取到请求资源的列表
    @Value("${authUrls.url}")
    private String authUrls;
    @Autowired
    private RedisTemplate redisTemplate;

    private AntPathMatcher antPathMatcher = new AntPathMatcher();
    /**   过滤器方法
     *
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //先获取用户的请求对象
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        //内部接口  不允许随意访问
        if(antPathMatcher.match("/**/inner/**",path)){
            //获取响应对象
            ServerHttpResponse response = exchange.getResponse();
            //不能访问  没有权限  209
            return out(response, ResultCodeEnum.PERMISSION);
        }
        //获取用户id
        String userId = getUserId(request);
        //获取临时用户id
        String userTempId = getUserTempId(request);
        //判断/api/*/auth/** 如果是这样的路径  那么应该登录

        if(antPathMatcher.match("/api/**/auth/**",path)){
            //说明没有登录
            if(StringUtils.isEmpty(userId)){
                //获取响应对象
                ServerHttpResponse response = exchange.getResponse();
                //不能访问  未登录 208
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }
        //验证用户请求的URL  未登录情况下不允许访问的路径
        if(null != authUrls){
            //循环判断
            for (String authUrl : authUrls.split(",")) {
                //判断path  中是否包含以上请求资源
                if(path.indexOf(authUrl)!=-1 && StringUtils.isEmpty(userId)){
                    //获取相应对象
                    ServerHttpResponse response = exchange.getResponse();
                    //303  由于请求的资源  存在另一个url  重定向
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI());
                    return response.setComplete();
                }
            }
        }
        //上述验证通过  我要把用户id  传递到各个微服务上
        if(!StringUtils.isEmpty(userId)||!StringUtils.isEmpty(userTempId)){

            if(!StringUtils.isEmpty(userId)){
                //存储一个userId
               request.mutate().header("userId",userId);
            }
            if(!StringUtils.isEmpty(userTempId)){
                //存储一个userTempId
                request.mutate().header("userTempId",userTempId);
            }

            //统一将用户id传递下取=去
            return chain.filter(exchange.mutate().request(request).build());
        }

        return chain.filter(exchange);
    }
    //获取用户id
    private String getUserId(ServerHttpRequest request) {
        //用户id存储在缓存
        String token = "";
        List<String> tokenList = request.getHeaders().get("token");
        //如果token没数据  那么走cookie
        if(null!= tokenList&& tokenList.size()>0){
            token = tokenList.get(0);
        }else {
            MultiValueMap<String, HttpCookie> cookies = request.getCookies();
            //List<HttpCookie> cookiesList = cookies.get("token");
            //getFirst 直接取第一个
            HttpCookie cookie = cookies.getFirst("token");
            if(null!=cookie){
                token = URLDecoder.decode(cookie.getValue());
            }
        }
        if(!StringUtils.isEmpty(token)){
            //才能从缓存中获取数据
            String userKey = "user:login:"+token;
            String userId = (String)redisTemplate.opsForValue().get(userKey);
            return userId;
        }

        return "";
    }
    //提示信息
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {

        //提示信息告诉用户  提示信息封装到ResultCodeEnum队象
        Result<Object> build = Result.build(null, resultCodeEnum);
        //将build 转化为字符串
        String resultStr = JSONObject.toJSONString(build);
        //将resultStr转换成一个字节数组
        byte[] bytes = resultStr.getBytes(StandardCharsets.UTF_8);
        //声明一个DatabBuffer
        DataBuffer wrap = response.bufferFactory().wrap(bytes);
        //设置信息输出格式
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        //将信息输出到页面
        return response.writeWith(Mono.just(wrap));
    }


    //再网关中获取临时用户id
    private String getUserTempId(ServerHttpRequest request){
        String userTempId = "";
        List<String> userTempIdList = request.getHeaders().get("userTempId");
        if(null != userTempIdList){
            userTempId = userTempIdList.get(0);
        }else {
            //从cookie 中获取临时用户id
            HttpCookie cookie = request.getCookies().getFirst("userTempId");
            if(null != cookie){
                userTempId = cookie.getValue();
            }
        }
        return userTempId;
    }
}
