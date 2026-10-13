package com.git.hui.jobclaw.gather.service;

import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvReader;
import com.git.hui.jobclaw.core.agent.extraction.SourceRecord;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** AI-GENERATED AIDEV-NOTE: 只按明确记录边界拆分，保留字段换行和 HTML 链接。 */
public final class GatherSourceRecords {
    private GatherSourceRecords() { }

    public static List<SourceRecord> rows(String header, List<String> rows) {
        List<SourceRecord> records = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            records.add(new SourceRecord("row-" + (i + 1), "表头：" + header + "\n数据：" + rows.get(i)));
        }
        return records;
    }

    public static List<SourceRecord> csv(String text) throws IOException {
        try (CsvReader reader = new CsvReader(new StringReader(text.replaceFirst("^\uFEFF", "")),
                CsvReadConfig.defaultConfig().setSkipEmptyRows(true).setErrorOnDifferentFieldCount(true))) {
            var rows = reader.read().getRows();
            if (rows.isEmpty()) {
                return List.of();
            }
            String header = JsonUtil.toStr(rows.getFirst().getRawList());
            return rows(header, rows.stream().skip(1).map(row -> JsonUtil.toStr(row.getRawList())).toList());
        }
    }

    public static List<SourceRecord> text(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var document = Jsoup.parse(text);
        var tables = document.select("table");
        if (tables.isEmpty()) {
            // 无法确认岗位边界时保留整段，超限明确失败，不按字符切断岗位。
            return List.of(new SourceRecord("text-1", text));
        }
        List<SourceRecord> records = new ArrayList<>();
        var context = document.clone();
        context.select("table,script,style").remove();
        for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
            Element table = tables.get(tableIndex);
            String header = table.select("thead").outerHtml();
            int rowIndex = 0;
            for (Element row : table.select("tr")) {
                if (row.closest("table") != table) {
                    continue;
                }
                if (!row.select("th").isEmpty()) {
                    if (header.isBlank()) {
                        header = row.outerHtml();
                    }
                    continue;
                }
                if (row.select("td").isEmpty()) {
                    continue;
                }
                // rowspan 会跨记录共享字段，保守保留整张表。
                if (!table.select("[rowspan]").isEmpty()) {
                    records.add(new SourceRecord("table-" + tableIndex, context.text() + "\n" + table.outerHtml()));
                    break;
                }
                records.add(new SourceRecord("table-" + tableIndex + "-row-" + (++rowIndex),
                        "公共上下文（仅辅助理解，不单独生成岗位）：" + context.text() + "\n"
                                + table.select("caption").text() + "\n表头：" + header + "\n当前记录：" + row.outerHtml()));
            }
        }
        return records.isEmpty() ? List.of(new SourceRecord("text-1", text)) : records;
    }
}
