package com.atguigu.gmall.common.cache;


import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.baomidou.mybatisplus.extension.api.R;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 利用aop 来实现缓存
 */
@Component
@Aspect
public class GmallCacheAspect {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;
    //@annotation(org.springframework.transaction.annotation.Transactional)
    //模拟Transactional 用法
    //返回值为Object  因为我们切面的方法不确定返回值类型 所以用Object
    @Around(" @annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint point){
        //声明一个Object  返回值
        Object result = null;
        //获取传递过来的参数
        Object[] args = point.getArgs();
        //获取方法上的签名，我们如何知道方法上是否又注解
        MethodSignature methodSignature  = (MethodSignature) point.getSignature();
        //得到注解
        GmallCache gmallCache = methodSignature.getMethod().getAnnotation(GmallCache.class);
        //获取缓存的前缀
        String prefix = gmallCache.prefix();
        //组成缓存的key
        String key = prefix+ Arrays.asList(args).toString();
        //从缓存中获取数据
        result = cacheHit(key,methodSignature);

        if(result!=null){
            //从缓存中获取 到  数据
            return result;
        }
        //缓存没有数据
        RLock lock = redissonClient.getLock(key + "lock");
        try {
            boolean res = lock.tryLock(100, 10, TimeUnit.SECONDS);
            if(res){
                //获取到分布式锁，丛数据库访问数据
                //如果访问到getSkuInfo 那么相当于 调用skuInfoDB
                result = point.proceed(point.getArgs());
                if(null==result){
                    Object o = new Object();
                    redisTemplate.opsForValue().set(key,JSONObject.toJSONString(o), RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                    return o;
                }
                redisTemplate.opsForValue().set(key,JSONObject.toJSONString(result), RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                return result;
            }else {
                Thread.sleep(1000);
                return cacheHit(key,methodSignature);
            }

        }catch (Exception e){
            e.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }finally {
            lock.unlock();
        }
        return result;
    }
    //从缓存中获取数据
    private Object cacheHit(String key ,MethodSignature methodSignature ) {
        //有key 可以从缓存获取数据
        String cache = (String) redisTemplate.opsForValue().get(key);
        //判断当前的字符串是否有值
        if(StringUtils.isNotBlank(cache)){
            //字符串时项目中多需要的那种数据类型
            //方法的返回值类型是什么 ，缓存就是存储的什么数据类型
            Class returnType = methodSignature.getReturnType();
            //现在将cache 转换成方法的返回值类型即可
            return JSONObject.parseObject(cache,returnType);
        }
        return null;
    }
}
