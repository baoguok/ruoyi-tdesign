package org.dromara.common.ratelimiter.annotation;

import org.dromara.common.ratelimiter.enums.LimitType;
import org.intellij.lang.annotations.Language;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 *
 * @author Lion Li
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {
    /**
     * 限流key
     * 限流key,支持使用Spring el表达式来动态获取方法上的参数值
     * 格式类似于  #code.id #{#code}
     */
    @Language("SpEL")
    String key() default "";

    /**
     * 限流时间,单位秒
     */
    int time() default 60;

    /**
     * 限流次数
     */
    int count() default 100;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.DEFAULT;

    /**
     * 提示消息 支持国际化 格式为 {code}
     */
    String message() default "{rate.limiter.message}";

    /**
     * 限流策略超时时间 默认一小时(策略存活时间 会清除已存在的策略数据。等待新请求的最大时间)
     */
    int timeout() default 3600;

}
