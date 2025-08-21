package xyz.mcutils.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.mcutils.backend.log.RequestTimingFilter;

@Getter @Log4j2(topic = "Config")
@Configuration
public class Config {
    public static Config INSTANCE;

    @Autowired
    private Environment environment;

    @Value("${public-url}")
    private String webPublicUrl;

    @PostConstruct
    public void onInitialize() {
        INSTANCE = this;
    }

    @Bean
    public FilterRegistrationBean<RequestTimingFilter> requestTimingFilter() {
        FilterRegistrationBean<RequestTimingFilter> filterRegistrationBean = new FilterRegistrationBean<>(new RequestTimingFilter());
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(1);
        filterRegistrationBean.setName("requestTimingFilter");
        return filterRegistrationBean;
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