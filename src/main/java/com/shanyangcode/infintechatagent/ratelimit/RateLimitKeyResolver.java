package com.shanyangcode.infintechatagent.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

@Component
public class RateLimitKeyResolver {

    public String resolveIdentifier(RateLimitTarget target, HttpServletRequest request, Object[] args) {
        return switch (target) {
            case USER -> resolveUserId(args);
            case IP -> resolveClientIp(request);
            case API -> request.getMethod() + ":" + request.getRequestURI();
        };
    }

    private String resolveUserId(Object[] args) {
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            BeanWrapperImpl beanWrapper = new BeanWrapperImpl(arg);
            if (beanWrapper.isReadableProperty("userId")) {
                Object userId = beanWrapper.getPropertyValue("userId");
                if (userId != null) {
                    return Objects.toString(userId);
                }
            }
        }
        return "anonymous";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
