package io.github.ajax1.campusradar.collect;

import io.github.ajax1.campusradar.collect.source.AlibabaCollector;
import io.github.ajax1.campusradar.collect.source.FeishuAtsCollector;
import io.github.ajax1.campusradar.collect.source.PddCollector;
import io.github.ajax1.campusradar.collect.source.TencentCollector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyDirectoryTest {

    private final CompanyDirectory directory = new CompanyDirectory(List.of(
            new FeishuAtsCollector("bytedance", "字节跳动", List.of("字节", "ByteDance", "抖音"),
                    "https://jobs.bytedance.com"),
            new FeishuAtsCollector("nio", "蔚来", List.of("NIO"), "https://nio.jobs.feishu.cn"),
            new AlibabaCollector(), new TencentCollector(), new PddCollector()));

    @Test
    void resolvesFullNamesAliasesAndCase() {
        assertThat(directory.resolve("字节跳动")).contains("字节跳动");
        assertThat(directory.resolve("字节")).contains("字节跳动");
        assertThat(directory.resolve(" bytedance ")).contains("字节跳动");
        assertThat(directory.resolve("阿里")).contains("阿里巴巴");
        assertThat(directory.resolve("pdd")).contains("拼多多");
    }

    @Test
    void resolvesWhenInputContainsName() {
        assertThat(directory.resolve("阿里巴巴集团")).contains("阿里巴巴");
        assertThat(directory.resolve("微信事业群")).contains("腾讯");
    }

    @Test
    void shortLatinAliasesOnlyMatchExactly() {
        assertThat(directory.resolve("NIO")).contains("蔚来");
        assertThat(directory.resolve("union pay")).isEmpty();
    }

    @Test
    void unknownCompanyIsEmpty() {
        assertThat(directory.resolve("华为")).isEmpty();
        assertThat(directory.resolve("")).isEmpty();
        assertThat(directory.companies()).containsExactly("字节跳动", "蔚来", "阿里巴巴", "腾讯", "拼多多");
    }
}
