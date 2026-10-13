package com.git.hui.jobclaw;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.git.hui.jobclaw.web.config.SiteConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.scheduler.Schedulers;

@Slf4j
@SpringBootApplication
@EnableJpaRepositories
@EntityScan
@EnableAsync
@EnableScheduling
@ServletComponentScan
public class JobClawApplication implements ApplicationRunner {
    @Value("${server.port:8080}")
    private Integer webPort;
    @Autowired
    private SiteConfig siteConfig;

    public static void main(String[] args) {
        initReactorExecutorService();
        SpringApplication.run(JobClawApplication.class, args);
    }


    /**
     * 在项目启动之前，注册Reactor 线程池装饰器，使用阿里的TTL进行包装，避免MCP Server在响应时，因为Reactor的异步执行，导致上下文ReqInfoContext丢失
     */
    private static void initReactorExecutorService() {
        // 注册ExecutorService装饰器，针对boundedElastic类型的调度器
        Schedulers.addExecutorServiceDecorator(
                "boundedElastic", // 目标调度器的名称（与Reactor默认boundedElastic名称匹配）
                (schedulerName, executorService) -> {
                    // 用TtlExecutors包装原始线程池，增强TTL上下文传递能力
                    if (log.isDebugEnabled()) {
                        log.debug("schedulerName -> {}", schedulerName);
                    }
                    return TtlExecutors.getTtlScheduledExecutorService(executorService);
                }
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        if (webPort != null) {
            String url = siteConfig.getWebSiteHost() + ":" + webPort;
            log.info("启动成功，点击进入首页: {}", url);
            // 本地开发环境自动打开浏览器
            if (BooleanUtils.isTrue(siteConfig.getAutoOpen())) {
                openBrowser(url);
            }
        }
    }

    /**
     * 自动打开浏览器访问指定URL
     */
    private void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(new java.net.URI(url));
                    log.info("已自动打开浏览器: {}", url);
                } else {
                    log.warn("当前系统不支持BROWSE操作，请手动访问: {}", url);
                }
            } else {
                log.warn("当前系统不支持Desktop API，尝试命令行方式启动: {}", url);
                // 方式2：兼容 Linux / 无桌面环境
                String os = System.getProperty("os.name").toLowerCase();
                Runtime runtime = Runtime.getRuntime();

                if (os.contains("win")) {
                    // Windows
                    runtime.exec("cmd /c start " + url);
                } else if (os.contains("mac")) {
                    // Mac
                    runtime.exec("open " + url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    // Linux
                    runtime.exec("xdg-open " + url);
                }
            }
        } catch (Exception e) {
            log.warn("自动打开浏览器失败，请手动访问: {}", url, e);
        }
    }
}
