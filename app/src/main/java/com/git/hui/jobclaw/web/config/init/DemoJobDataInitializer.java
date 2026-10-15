package com.git.hui.jobclaw.web.config.init;

import com.git.hui.jobclaw.constants.gather.GatherRunnerTypes;
import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 本地开发数据初始化：空的 H2 职位库提供几条可体验的演示岗位。
 *
 * <p>仅在 dev 配置开启且职位表为空时执行；已有数据（包括全部删除的记录）不会被覆盖。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "jobclaw.demo-data", name = "enabled", havingValue = "true")
// AI-GENERATED AIDEV-NOTE: 仅为空的本地 H2 库写入体验数据。
public class DemoJobDataInitializer implements ApplicationRunner {
    private final OcRepository ocRepository;

    public DemoJobDataInitializer(OcRepository ocRepository) {
        this.ocRepository = ocRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ocRepository.count() > 0) {
            return;
        }

        Date now = new Date();
        List<OcInfoEntity> jobs = List.of(
                demoJob("示例科技（JobClaw演示）", "民企", "互联网软件", "北京", "秋招", "2027年毕业生",
                        "Java 后端开发工程师（校招）", "2026-12-31", now),
                demoJob("云程信息（JobClaw演示）", "民企", "企业服务", "上海", "日常实习", "2027年毕业生",
                        "Java 后端开发实习生", "招满为止", now),
                demoJob("星河数据（JobClaw演示）", "外企", "数据服务", "深圳", "秋招提前批", "2027年毕业生",
                        "后端开发工程师（Java）", "2026-11-30", now),
                demoJob("青山智能（JobClaw演示）", "央国企", "智能制造", "杭州", "日常实习", "2026年毕业生",
                        "Java 服务端实习生", "招满为止", now)
        );
        ocRepository.saveAllAndFlush(jobs);
        log.info("本地演示数据已准备：{} 条岗位", jobs.size());
    }

    private OcInfoEntity demoJob(String companyName, String companyType, String industry,
                                 String location, String recruitmentType, String target,
                                 String position, String deadline, Date now) {
        return new OcInfoEntity()
                .setCompanyName(companyName)
                .setDraftId(0L)
                .setCompanyType(companyType)
                .setCompanyIndustry(industry)
                .setJobLocation(location)
                .setRecruitmentType(recruitmentType)
                .setRecruitmentTarget(target)
                .setPosition(position)
                .setDeliveryProgress("未投递")
                .setLastUpdatedTime(now)
                .setDeadline(deadline)
                // 演示数据不伪造真实投递来源，避免用户误点失效链接。
                .setRelatedLink("")
                .setJobAnnouncement("JobClaw 本地演示岗位，仅用于体验搜索、详情、保存和投递跟踪功能；无真实投递链接。")
                .setInternalReferralCode("")
                .setRemarks("演示数据，不代表真实招聘信息。")
                .setRunnerType(GatherRunnerTypes.SYSTEM)
                .setState(OcStateEnum.PUBLISHED.getValue())
                .setCreateTime(now)
                .setUpdateTime(now);
    }
}
