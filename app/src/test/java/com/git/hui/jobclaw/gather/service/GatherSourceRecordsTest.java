package com.git.hui.jobclaw.gather.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** AI-GENERATED AIDEV-NOTE: 验证表格逻辑边界，不丢失链接和多行字段。 */
class GatherSourceRecordsTest {
    @Test
    void csvQuotedNewlineAndCommaRemainInOneRecord() throws Exception {
        var records = GatherSourceRecords.csv("\uFEFF公司,职责\r\nA,\"开发,维护\nJava服务\"\r\nB,测试\r\n");
        assertEquals(2, records.size());
        assertTrue(records.getFirst().content().contains("开发,维护\\nJava服务"));
        assertTrue(records.getFirst().content().contains("职责"));
    }

    @Test
    void htmlRowsCarryHeadersAndLinks() {
        var records = GatherSourceRecords.text("<h1>2027校招</h1><table><tr><th>公司</th><th>链接</th></tr>"
                + "<tr><td>A</td><td><a href='https://a.example'>投递</a></td></tr>"
                + "<tr><td>B</td><td>无</td></tr></table>");
        assertEquals(2, records.size());
        assertTrue(records.getFirst().content().contains("2027校招"));
        assertTrue(records.getFirst().content().contains("公司"));
        assertTrue(records.getFirst().content().contains("https://a.example"));
    }

    @Test
    void mergedRowsRemainTogether() {
        var records = GatherSourceRecords.text("<table><tr><td rowspan='2'>A公司</td><td>开发</td></tr>"
                + "<tr><td>测试</td></tr></table>");
        assertEquals(1, records.size());
        assertTrue(records.getFirst().content().contains("测试"));
    }

    @Test
    void unstructuredTextIsNotCutInsideAJob() {
        String text = "公司A\n岗位：Java\n职责：开发\n\n条件：本科";
        assertEquals(text, GatherSourceRecords.text(text).getFirst().content());
        assertTrue(GatherSourceRecords.text(" ").isEmpty());
    }
}
