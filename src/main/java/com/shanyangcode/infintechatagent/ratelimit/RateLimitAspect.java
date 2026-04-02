package com.shanyangcode.infintechatagent.ratelimit;

import com.shanyangcode.infintechatagent.Exception.BusinessException;
import com.shanyangcode.infintechatagent.common.ErrorCode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect //切面
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RateLimitKeyResolver keyResolver;

    /**
     *
     * @param joinPoint  被拦截的方法
     * @param rateLimit  被拦截的方法上的配置规则
     * @return
     * @throws Throwable
     */

    @Around("@annotation(rateLimit)")
    public Object doRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = currentRequest();
        Object[] args = joinPoint.getArgs();

        for (RateLimitRule rule : rateLimit.rules()) {
            String identifier = keyResolver.resolveIdentifier(rule.target(), request, args);

            String limiterKey = buildLimiterKey(rateLimit.keyPrefix(), rule.target(), identifier);
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(limiterKey);
            rateLimiter.trySetRate(RateType.OVERALL, rule.rate(), rule.rateInterval(), rule.rateIntervalUnit());

            if (!rateLimiter.tryAcquire(1)) {
                log.warn("Rate limit exceeded, key={}, identifier={}, uri={}", limiterKey, identifier, request.getRequestURI());
                throw new BusinessException(ErrorCode.AI_MODEL_RATE_LIMIT, buildErrorMessage(rateLimit, rule.target()));
            }
        }

        return joinPoint.proceed();
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            throw new IllegalStateException("Current request is unavailable");
        }
        return servletRequestAttributes.getRequest();
    }

    private String buildLimiterKey(String keyPrefix, RateLimitTarget target, String identifier) {
        return "rate_limit:%s:%s:%s".formatted(keyPrefix, target.name().toLowerCase(), identifier);
    }

    private String buildErrorMessage(RateLimit rateLimit, RateLimitTarget target) {
        if (!rateLimit.message().isBlank()) {
            return rateLimit.message();
        }
        return switch (target) {
            case USER -> "当前用户请求过于频繁，请稍后重试";
            case IP -> "当前IP请求过于频繁，请稍后重试";
            case API -> "当前接口访问过于频繁，请稍后重试";
        };
    }
}
