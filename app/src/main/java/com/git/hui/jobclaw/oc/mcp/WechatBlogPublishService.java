package com.git.hui.jobclaw.oc.mcp;

import com.git.hui.jobclaw.gather.service.ai.OcAiModelContext;
import com.git.hui.jobclaw.oc.service.OcService;
import com.git.hui.jobclaw.util.DateUtil;
import com.git.hui.jobclaw.web.model.res.OcVo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * 微信公众号博文发布Agent
 *
 * @date 2025/8/22
 */
@Service
public class WechatBlogPublishService {
    private final OcAiModelContext ocAiModelContext;

    private final OcService ocService;

    private static final String companyTemplate = """
            ### {companyName}

            公司类型：{companyType}
                        
            行业属性：{companyIndustry}
                        
            工作地点：{jobLocation}
                        
            职位类型：{position}
                        
            招聘对象：{recruitmentTarget}
                        
            目标职位：{recruitmentType}
                            
            访问链接: <a href="{relatedLink}">{relatedLink}</a>
                        
            """;

    private static final String blogs = """
            帮我将下面这篇markdown格式的文章内容发布到微信公众号的草稿箱，使用 OrangeHeart 主题

            ```markdown
            ---
            title: {title}
            author: JobClaw Contributors
            cover: /favicon.svg
            ---
                            
            JobClaw今日上新校招岗位{ocCnt}条、实习岗位{internshipCnt}条，找工作的小伙伴重点关注一波哦
                            
            {text}
            ```

            """;


    public WechatBlogPublishService(OcAiModelContext ocAiModelContext, OcService ocService) {
        this.ocAiModelContext = ocAiModelContext;
        this.ocService = ocService;
    }

    /**
     * 发布今天上新的公众号文章内容
     *
     * @return
     */
    public String publishTodayOcInfo() {
        // 查询今天新上的oc列表信息
        List<OcVo> list = ocService.searchTodayOcList();
        if (CollectionUtils.isEmpty(list)) {
            return "今日没有上新";
        }

        StringBuilder ocInfo = new StringBuilder();
        int ocCnt = 0;
        StringBuilder internship = new StringBuilder();
        int internshipCnt = 0;
        for (OcVo vo : list) {
            String text = new PromptTemplate(companyTemplate)
                    .render(Map.of("companyName", vo.getCompanyName()
                                    , "companyType", vo.getCompanyType()
                                    , "companyIndustry", vo.getCompanyIndustry()
                                    , "jobLocation", vo.getJobLocation()
                                    , "position", vo.getPosition()
                                    , "recruitmentTarget", vo.getRecruitmentTarget()
                                    , "recruitmentType", vo.getRecruitmentType()
                                    , "relatedLink", vo.getRelatedLink()
                            )
                    );
            if (vo.getRecruitmentType().contains("实习")) {
                internshipCnt++;
                internship.append(text);
            } else {
                ocCnt++;
                ocInfo.append(text);
            }
        }

        String text = "";
        if (ocCnt > 0) {
            text += "\n## 校招信息\n\n" + ocInfo.toString();
        }
        if (internshipCnt > 0) {
            text += "\n## 实习信息\n\n" + internship.toString();
        }

        String promp = new PromptTemplate(blogs)
                .render(Map.of("title", "JobClaw快讯【" + DateUtil.todayStr() + "】"
                        , "text", text
                        , "ocCnt", ocCnt
                        , "internshipCnt", internshipCnt
                ));

        return this.ocAiModelContext
                .getMainChatClient()
                .prompt()
                .system("你现在是一个善于发布公众号的专家，善于使用各种工具。我会给你提供发布公众号的工具调用，请你基于这些工具调用来实现发布公众号。不需要二次确认，直接生成微信公众号文章并自动发布")
                .user(promp)
                .call().content();
    }

}
