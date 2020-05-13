package com.atguigu.gmall.common.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)//使用再方法上
@Retention(RetentionPolicy.RUNTIME)  // 注解的生命周期  运行时
public @interface GmallCache {
    //定义一个字段 作为缓存的key来使用
    String prefix() default "cache";

}
