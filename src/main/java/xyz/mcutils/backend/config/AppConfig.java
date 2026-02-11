package xyz.mcutils.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.mcutils.backend.filter.MetricsAuthFilter;
import xyz.mcutils.backend.filter.SecurityHeadersFilter;

@Getter
@Slf4j
@Configuration
public class AppConfig {
    public static AppConfig INSTANCE;

    @Autowired
    private Environment environment;

    @Value("${mc-utils.public-url}")
    private String webPublicUrl;

    @Value("${mc-utils.metrics-token}")
    private String metricsToken;

    @PostConstruct
    public void onInitialize() {
        INSTANCE = this;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> bean = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(-1); // Run first so headers are on every response
        bean.setName("securityHeadersFilter");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<MetricsAuthFilter> metricsAuthFilter() {
        FilterRegistrationBean<MetricsAuthFilter> bean = new FilterRegistrationBean<>(new MetricsAuthFilter(metricsToken));
        bean.addUrlPatterns("/metrics");
        bean.setOrder(0); // Run before other filters
        bean.setName("metricsAuthFilter");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> filterRegistrationBean = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(2);
        filterRegistrationBean.setName("etagFilter");
        return filterRegistrationBean;
    }

    @Bean
    public WebMvcConfigurer configureCors() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                // Allow all origins to access the API
                registry.addMapping("/**")
                        .allowedOrigins("*") // Allow all origins
                        .allowedMethods("*") // Allow all methods
                        .allowedHeaders("*"); // Allow all headers
            }
        };
    }
}