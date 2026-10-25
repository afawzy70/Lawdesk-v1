package com.lawdesk.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC settings shared by every module. */
@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthorizeBeforeBindingInterceptor());
        // After authorization, so a caller without the permission gets 403 and not a parameter error (QAD-026).
        registry.addInterceptor(new PagingValidationInterceptor());
    }
}
