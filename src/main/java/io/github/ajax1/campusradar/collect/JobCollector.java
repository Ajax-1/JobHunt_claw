package io.github.ajax1.campusradar.collect;

import java.util.List;

/**
 * 一个数据源一个实现。实现类只负责「请求 + 解析」，入库、去重、下线判断都在 {@link CollectService}。
 */
public interface JobCollector {

    /**
     * 数据源编码，入库后作为 source 字段，如 tencent
     */
    String source();

    /**
     * 展示用公司名
     */
    String company();

    /**
     * 公司的常见叫法（简称、英文名、子品牌），Agent 传这些也能匹配到 {@link #company()}
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * 拉取该来源当前在招的全部校招、实习岗位（社招需过滤掉）
     */
    List<RawJob> collect(HttpFetcher http);

    /**
     * 列表接口不带 JD 的来源返回 true，框架会只对新岗位调用 {@link #fetchDetail}
     */
    default boolean needsDetail() {
        return false;
    }

    /**
     * 返回 null 表示详情页显示岗位已失效（列表还没同步），本轮跳过该岗位，它会按「未出现」被下线
     */
    default RawJob fetchDetail(RawJob summary, HttpFetcher http) {
        return summary;
    }
}
