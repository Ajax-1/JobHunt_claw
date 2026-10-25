package io.github.ajax1.campusradar.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProfileServiceTest {

    @Autowired
    ProfileService service;
    @Autowired
    UserProfileRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private static ProfileView change(Integer gradYear, List<String> roles, List<String> cities,
                                      List<String> types, List<String> preferred) {
        return new ProfileView(gradYear, null, null, roles, null, cities, types, preferred, null, null, null, null);
    }

    @Test
    void emptyProfileBeforeFirstUpdate() {
        ProfileView v = service.view();
        assertThat(v.targetRoles()).isEmpty();
        assertThat(v.updatedAt()).isNull();
    }

    @Test
    void partialUpdateKeepsUntouchedFieldsAndEmptyListClears() {
        service.update(change(2027, List.of("后端", " Java ", "后端", ""), List.of("北京市", "上海"),
                List.of("校招"), List.of("字节", "阿里巴巴集团")));

        ProfileView v = service.update(change(null, null, List.of(), null, null));
        assertThat(v.gradYear()).isEqualTo(2027);
        assertThat(v.targetRoles()).containsExactly("后端", "Java");
        assertThat(v.cities()).isEmpty();
        assertThat(v.recruitTypes()).containsExactly("校招");
        assertThat(v.preferredCompanies()).containsExactly("字节跳动", "阿里巴巴");
    }

    @Test
    void normalizesCities() {
        assertThat(service.update(change(null, null, List.of("北京市", "北京", "杭州"), null, null)).cities())
                .containsExactly("北京", "杭州");
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> service.update(change(null, null, null, List.of("社招"), null)))
                .hasMessageContaining("校招、实习");
        assertThatThrownBy(() -> service.update(change(null, null, null, null, List.of("华为"))))
                .hasMessageContaining("没有收录公司「华为」");
        assertThatThrownBy(() -> service.update(change(27, null, null, null, null)))
                .hasMessageContaining("四位年份");
    }
}
