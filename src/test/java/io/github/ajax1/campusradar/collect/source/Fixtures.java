package io.github.ajax1.campusradar.collect.source;

import io.github.ajax1.campusradar.collect.Json;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 读取 src/test/resources/fixtures 下录制的真实接口响应（2026-10-24 录制，每家裁剪到 3 条）
 */
final class Fixtures {

    private Fixtures() {
    }

    static JsonNode load(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("fixture 不存在: " + name);
            }
            return Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
