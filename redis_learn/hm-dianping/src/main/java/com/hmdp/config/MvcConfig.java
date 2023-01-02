package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author hongyuan.shan
 * @date 2023/01/02 21:40
 * @description
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                  "/shop/**",
                  "/voucher/**",
                  "/shop-type/**",
                  "/upload/**",
                  "/blog/hot",
                  "/user/code",
                  "/user/login"
                );
    }
}
