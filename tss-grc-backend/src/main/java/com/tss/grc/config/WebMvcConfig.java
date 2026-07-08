package com.tss.grc.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Spring MVC configuration — view controllers, resource handlers,
 * CORS (disabled in production), default page mapping, and the export
 * audit interceptor.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ExportAuditInterceptor exportAuditInterceptor;

    /** Record every file download (Content-Disposition: attachment) to the audit log. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(exportAuditInterceptor).addPathPatterns("/api/**");
    }

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
