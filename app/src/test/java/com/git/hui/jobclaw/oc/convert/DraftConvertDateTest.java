package com.git.hui.jobclaw.oc.convert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** AI-GENERATED AIDEV-NOTE: 同一天的模型日期表示必须产生相同的草稿去重字段。 */
class DraftConvertDateTest {
    @Test
    void equivalentDatesHaveTheSameIdentityValue() {
        for (String input : new String[]{"2026-09-13 00:00:00", "2026年9月13日", "2026-09-13",
                "2026/9/13", "2026-09-13T00:00:00+08:00"}) {
            assertEquals("2026年09月13日", DraftConvert.normalizeUpdatedDate(input));
        }
    }

    @Test
    void unknownAndInvalidDatesAreNotInvented() {
        assertNull(DraftConvert.normalizeUpdatedDate(null));
        assertEquals("未知", DraftConvert.normalizeUpdatedDate("未知"));
        assertEquals("2026-02-30", DraftConvert.normalizeUpdatedDate("2026-02-30"));
    }
}
