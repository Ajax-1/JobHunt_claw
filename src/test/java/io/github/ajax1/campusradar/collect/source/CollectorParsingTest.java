package io.github.ajax1.campusradar.collect.source;

import io.github.ajax1.campusradar.collect.RawJob;
import io.github.ajax1.campusradar.job.RecruitType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用录制的真实响应验证各家解析逻辑；接口改版时先更新 fixture，再看这里哪条断言挂掉
 */
class CollectorParsingTest {

    @Test
    void tencentListThenDetail() {
        List<RawJob> list = TencentCollector.parseList(Fixtures.load("tencent-list"));
        assertCommon(list, "join.qq.com/post_detail.html?postid=");
        assertThat(list).allSatisfy(j -> assertThat(j.description()).isNull());

        JsonNode detail = Fixtures.load("tencent-detail");
        RawJob summary = list.stream()
                .filter(j -> j.externalId().equals(detail.path("data").path("postId").asString()))
                .findFirst()
                .orElse(list.getFirst());
        RawJob full = TencentCollector.parseDetail(summary, detail.path("data"));
        assertThat(full.description()).isNotBlank();
        assertThat(full.requirement()).contains("加分项");
        assertThat(full.category()).isEqualTo("技术");
        assertThat(full.cities()).contains("深圳", "北京").doesNotContain("深圳总部");
    }

    @Test
    void tencentQingyunTopicUsesTopicFields() {
        RawJob summary = new RawJob("1", "腾讯视频-多模态视频实时生成研究", null, RecruitType.INTERN, null,
                List.of(), null, null, "https://join.qq.com/post_detail.html?postid=1", null);
        JsonNode d = io.github.ajax1.campusradar.collect.Json.parse("""
                {"tidName":"青云课题","isQingyun":1,"techTagName":"多模态、视觉、语音","desc":"","request":"",
                 "topicDetail":"课题背景：多模态生成","topicRequirement":"1、扎实的视觉基础",
                 "internBonus":"同时招聘低年级实习生","workCityList":["深圳总部","北京"]}""");
        RawJob full = TencentCollector.parseDetail(summary, d);
        assertThat(full.description()).isEqualTo("课题背景：多模态生成");
        assertThat(full.requirement()).startsWith("1、扎实的视觉基础").contains("同时招聘低年级实习生");
        assertThat(full.category()).isEqualTo("多模态、视觉、语音");
        assertThat(full.cities()).containsExactly("深圳", "北京");
    }

    @Test
    void tencentRecruitType() {
        assertThat(TencentCollector.recruitType("应届毕业生")).isEqualTo(RecruitType.CAMPUS);
        assertThat(TencentCollector.recruitType("实习生")).isEqualTo(RecruitType.INTERN);
    }

    @Test
    void byteDanceViaFeishuAdapter() {
        List<RawJob> list = FeishuAtsCollector.parse(Fixtures.load("bytedance"), "https://jobs.bytedance.com");
        assertCommon(list, "jobs.bytedance.com/campus/position/");
        assertWithJd(list);
        assertThat(list).allSatisfy(j -> assertThat(j.publishedAt()).isNotNull());
    }

    @Test
    void feishuSiteWithRecruitType() {
        List<RawJob> list = FeishuAtsCollector.parse(Fixtures.load("feishu-nio"), "https://nio.jobs.feishu.cn");
        assertCommon(list, "nio.jobs.feishu.cn/campus/position/");
        assertWithJd(list);
        assertThat(list).extracting(RawJob::recruitType)
                .containsExactly(RecruitType.CAMPUS, RecruitType.CAMPUS, RecruitType.INTERN);
    }

    @Test
    void feishuSiteWithoutRecruitTypeFallsBackToTitle() {
        List<RawJob> list = FeishuAtsCollector.parse(Fixtures.load("feishu-desaysv"), "https://yesv-desaysv.jobs.feishu.cn");
        assertCommon(list, "yesv-desaysv.jobs.feishu.cn/campus/position/");
        assertThat(list).extracting(RawJob::recruitType)
                .containsExactly(RecruitType.INTERN, RecruitType.INTERN, RecruitType.CAMPUS);
        assertThat(list.get(2).gradYear()).isEqualTo(2027);
    }

    @Test
    void meituanCampusAndIntern() {
        List<RawJob> campus = MeituanCollector.parse(Fixtures.load("meituan"), RecruitType.CAMPUS);
        assertCommon(campus, "zhaopin.meituan.com/web/position/detail?jobUnionId=");
        assertWithJd(campus);

        List<RawJob> intern = MeituanCollector.parse(Fixtures.load("meituan-intern"), RecruitType.INTERN);
        assertThat(intern).isNotEmpty().allSatisfy(j -> assertThat(j.recruitType()).isEqualTo(RecruitType.INTERN));
    }

    @Test
    void baiduStripsCityPrefixAndCode() {
        List<RawJob> list = BaiduCollector.parse(Fixtures.load("baidu"), "GRADUATE");
        assertCommon(list, "talent.baidu.com/jobs/detail/GRADUATE/");
        assertWithJd(list);
        assertThat(list).allSatisfy(j -> assertThat(j.title()).doesNotContain("(J"));
        assertThat(BaiduCollector.cleanTitle("北京-网络研发工程师(J100700)", List.of("北京")))
                .isEqualTo("网络研发工程师");
        assertThat(BaiduCollector.cleanTitle("C++-研发工程师", List.of("北京")))
                .isEqualTo("C++-研发工程师");
    }

    @Test
    void jdCitiesFromRequirements() {
        List<RawJob> list = JdCollector.parse(Fixtures.load("jd"));
        assertCommon(list, "campus.jd.com/#/details?id=");
        assertWithJd(list);
        assertThat(list.getFirst().cities()).contains("北京");
    }

    @Test
    void alibabaBatchesAndPositions() {
        List<AlibabaCollector.Batch> batches = AlibabaCollector.parseBatches(Fixtures.load("alibaba-batch"));
        assertThat(batches).extracting(AlibabaCollector.Batch::type)
                .contains(RecruitType.CAMPUS, RecruitType.INTERN);

        AlibabaCollector.Batch graduate = batches.stream()
                .filter(b -> b.type() == RecruitType.CAMPUS).findFirst().orElseThrow();
        List<RawJob> list = AlibabaCollector.parse(Fixtures.load("alibaba"), graduate);
        assertCommon(list, "campus-talent.alibaba.com/campus/position/");
        assertWithJd(list);
        assertThat(list).allSatisfy(j -> assertThat(j.gradYear()).isEqualTo(2027));
    }

    @Test
    void kuaishou() {
        List<RawJob> list = KuaishouCollector.parse(Fixtures.load("kuaishou"));
        assertCommon(list, "campus.kuaishou.cn/recruit/campus/e/#/campus/job-info/");
        assertWithJd(list);
        assertThat(list).allSatisfy(j -> assertThat(j.publishedAt()).isNotNull());
    }

    @Test
    void pdd() {
        List<RawJob> list = PddCollector.parse(Fixtures.load("pdd"));
        assertCommon(list, "careers.pddglobalhr.com/campus/grad/detail?positionId=");
        assertThat(list).allSatisfy(j -> assertThat(j.description()).isNotBlank());
        assertThat(list).allSatisfy(j -> assertThat(j.gradYear()).isEqualTo(2027));
    }

    private static void assertCommon(List<RawJob> list, String urlPart) {
        assertThat(list).isNotEmpty().allSatisfy(j -> {
            assertThat(j.externalId()).isNotBlank();
            assertThat(j.title()).isNotBlank();
            assertThat(j.recruitType()).isNotNull();
            assertThat(j.applyUrl()).contains(urlPart).doesNotEndWith("null");
            assertThat(j.cities()).doesNotContain("null", "");
        });
    }

    private static void assertWithJd(List<RawJob> list) {
        assertThat(list).allSatisfy(j -> assertThat(j.description()).isNotBlank());
    }
}
