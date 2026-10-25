package io.github.ajax1.campusradar.job;

/**
 * 招聘类型。社招岗位在采集阶段就被过滤掉，不入库。
 */
public enum RecruitType {
    CAMPUS("校招"),
    INTERN("实习");

    private final String label;

    RecruitType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 兼容中文和英文两种写法，无法识别时返回 null
     */
    public static RecruitType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (RecruitType t : values()) {
            if (t.name().equalsIgnoreCase(v) || t.label.equals(v)) {
                return t;
            }
        }
        return null;
    }
}
