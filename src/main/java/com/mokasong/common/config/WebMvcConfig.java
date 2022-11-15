package com.mokasong.common.config;

import com.mokasong.common.interceptor.AuthorityCheckInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthorityCheckInterceptor interceptor;

    public WebMvcConfig(AuthorityCheckInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .excludePathPatterns("/swagger-resources/**")
                .excludePathPatterns("/v3/**");
    }
}
