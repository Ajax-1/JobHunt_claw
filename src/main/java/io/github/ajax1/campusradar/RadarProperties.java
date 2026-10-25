package io.github.ajax1.campusradar;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 应用配置，对应 application.yml 里的 radar.*
 *
 * @param collect     采集相关配置
 * @param mcp         MCP 访问控制
 * @param feishuSites 使用飞书招聘系统的公司站点，每个站点生成一个采集器
 */
@ConfigurationProperties(prefix = "radar")
public record RadarProperties(Collect collect, Mcp mcp, List<FeishuSite> feishuSites) {

    public RadarProperties {
        feishuSites = feishuSites == null ? List.of() : List.copyOf(feishuSites);
    }

    /**
     * @param cron              定时采集的 cron 表达式
     * @param zone              cron 使用的时区
     * @param runOnStartup      启动后是否立即跑一轮采集
     * @param requestIntervalMs 同一数据源两次列表请求之间的最小间隔
     * @param detailIntervalMs  逐个拉取详情时的最小间隔
     * @param disabledSources   暂停采集的数据源编码
     */
    public record Collect(String cron, String zone, boolean runOnStartup, long requestIntervalMs,
                          long detailIntervalMs, List<String> disabledSources) {
        public Collect {
            disabledSources = disabledSources == null ? List.of() : List.copyOf(disabledSources);
        }
    }

    /**
     * @param source  数据源编码，如 nio
     * @param company 公司名，如 蔚来
     * @param baseUrl 站点根地址，如 https://nio.jobs.feishu.cn
     * @param aliases 公司的其他叫法
     */
    public record FeishuSite(String source, String company, String baseUrl, List<String> aliases) {
    }

    /**
     * @param token 非空时，访问 /mcp 必须带 Authorization: Bearer &lt;token&gt;
     */
    public record Mcp(String token) {
    }
}
