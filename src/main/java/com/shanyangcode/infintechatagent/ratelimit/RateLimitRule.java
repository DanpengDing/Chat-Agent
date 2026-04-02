package com.shanyangcode.infintechatagent.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.redisson.api.RateIntervalUnit;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
@Documented
public @interface RateLimitRule {
    //限流目标
    RateLimitTarget target();

    //限流次数
    long rate();

    //期间
    long rateInterval();

    //时间单位
    RateIntervalUnit rateIntervalUnit() default RateIntervalUnit.SECONDS;
}
