package com.git.hui.jobclaw.web.config;

import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.web.hook.interceptor.PermissionCheckInterceptor;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * web配置
 *
 * @date 2025/7/16
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private String[] configs;

    private String[] staticConfigs() {
        String template = "spring.web.resources.static-locations[%d]";
        int i = 0;
        List<String> list = new ArrayList<>();
        do {
            String item = SpringUtil.getConfig(String.format(template, i));
            if (StringUtils.isBlank(item)) {
                break;
            }
            list.add(item);
            i++;
        } while (true);
        return list.toArray(new String[0]);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (configs == null) {
            configs = staticConfigs();
        }

        registry.addResourceHandler("/**")
                .addResourceLocations(configs)
                .setCachePeriod(0).resourceChain(true)
                .addResolver(new AbstractResourceResolver() {
                    @Override
                    protected Resource resolveResourceInternal(HttpServletRequest request,
                                                               String requestPath,
                                                               List<? extends Resource> locations, ResourceResolverChain chain) {
                        if (requestPath.startsWith("h2-console")) {
                            return null;
                        }

                        for (Resource location : locations) {
                            try {
                                Resource resource = location.createRelative(requestPath);
                                if (resource.exists() && resource.isReadable()) {
                                    return resource;
                                }

                                resource = location.createRelative(requestPath + ".html");
                                if (resource.exists() && resource.isReadable()) {
                                    return resource;
                                }
                            } catch (IOException ignored) {
                            }
                        }

                        return new ClassPathResource("static/index.html");
                    }

                    @Override
                    protected String resolveUrlPathInternal(String requestPath, List<? extends Resource> locations, ResourceResolverChain chain) {
                        return requestPath;
                    }
                });
    }

    @Autowired
    private PermissionCheckInterceptor permissionCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionCheckInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/wx");
    }

    @SuppressWarnings("unchecked")
    @Bean
    @ConditionalOnClass(name = "org.h2.server.web.JakartaWebServlet")
    public ServletRegistrationBean<Servlet> h2Console(
            @Value("${spring.h2.console.path:/h2-console}") String path) throws Exception {
        Class<? extends Servlet> servletClass =
                (Class<? extends Servlet>) Class.forName("org.h2.server.web.JakartaWebServlet");
        ServletRegistrationBean<Servlet> registration =
                new ServletRegistrationBean<>(servletClass.getDeclaredConstructor().newInstance(), path + "/*");
        registration.setLoadOnStartup(1);
        return registration;
    }

}
