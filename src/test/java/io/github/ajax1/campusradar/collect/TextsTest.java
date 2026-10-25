package io.github.ajax1.campusradar.collect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextsTest {

    @Test
    void normalizesCities() {
        assertThat(Texts.cities(List.of("深圳总部 北京 上海 ")))
                .containsExactly("深圳", "北京", "上海");
        assertThat(Texts.cities(List.of("北京市-北京市", "上海市", "北京市")))
                .containsExactly("北京", "上海");
        assertThat(Texts.cities(List.of("远程面试", "全国多省区"))).containsExactly("全国多省区");
    }

    @Test
    void joinsCitiesForExactMatch() {
        assertThat(Texts.joinCities(List.of("北京", "上海"))).isEqualTo(",北京,上海,");
        assertThat(Texts.joinCities(List.of())).isNull();
    }

    @Test
    void extractsGradYear() {
        assertThat(Texts.gradYear("阿里巴巴2027届应届生")).isEqualTo(2027);
        assertThat(Texts.gradYear("27届秋招-算法工程师")).isEqualTo(2027);
        assertThat(Texts.gradYear(null, "应届毕业生")).isNull();
    }

    @Test
    void hashIsStableAndSensitive() {
        assertThat(Texts.hash("a", null, "b")).isEqualTo(Texts.hash("a", null, "b"));
        assertThat(Texts.hash("a", "b")).isNotEqualTo(Texts.hash("ab"));
    }
}
