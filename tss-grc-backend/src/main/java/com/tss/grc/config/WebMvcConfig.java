package com.tss.grc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Spring MVC configuration — view controllers, resource handlers,
 * CORS (disabled in production), and default page mapping.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Map root "/" to the dashboard redirect. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/dashboard");
    }

    /**
     * Static resource cache headers.
     * CSS/JS/images are fingerprinted via Thymeleaf @{} URLs; serve with
     * a moderate cache TTL to reduce repeat requests in production.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCachePeriod(86400);

        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCachePeriod(604800);
    }
}
