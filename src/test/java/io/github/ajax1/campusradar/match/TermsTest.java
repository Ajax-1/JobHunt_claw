package io.github.ajax1.campusradar.match;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TermsTest {

    @Test
    void latinTermsNeedWordBoundaries() {
        assertThat(Terms.contains("熟悉 javascript 和 react", "java")).isFalse();
        assertThat(Terms.contains("熟悉java/c++开发", "java")).isTrue();
        assertThat(Terms.contains("熟悉java开发", "Java")).isTrue();
        assertThat(Terms.contains("google cloud 经验", "go")).isFalse();
        assertThat(Terms.contains("有 mongodb 经验", "go")).isFalse();
        assertThat(Terms.contains("使用 go 语言开发", "Go")).isTrue();
        assertThat(Terms.contains("redisson 分布式锁", "redis")).isFalse();
        assertThat(Terms.contains("熟悉redis、mysql", "redis")).isTrue();
    }

    @Test
    void symbolsInsideTermsStillMatch() {
        assertThat(Terms.contains("精通c++和python", "C++")).isTrue();
        assertThat(Terms.contains("熟悉 c# / .net", "c#")).isTrue();
        assertThat(Terms.contains("会 node.js", "node.js")).isTrue();
    }

    @Test
    void synonymsCount() {
        assertThat(Terms.contains("golang 后端开发", "go")).isTrue();
        assertThat(Terms.contains("熟悉 kubernetes 运维", "k8s")).isTrue();
        assertThat(Terms.contains("llm 推理优化", "大模型")).isTrue();
        assertThat(Terms.variants("Go")).contains("go", "golang");
    }

    @Test
    void chineseTermsAreSubstrings() {
        assertThat(Terms.contains("大模型应用开发工程师", "大模型应用")).isTrue();
        assertThat(Terms.contains("服务端开发", "后端")).isFalse();
    }
}
