package com.gamesaves.gamesaves.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XssFilter 单元测试 — 验证 XSS 向量被正确清洗
 */
class XssFilterTest {

    @Test
    void sanitize_null_returnsNull() {
        assertNull(XssFilter.sanitize(null));
    }

    @Test
    void sanitize_plainText_unchanged() {
        assertEquals("Hello World", XssFilter.sanitize("Hello World"));
        assertEquals("你好世界", XssFilter.sanitize("你好世界"));
        assertEquals("Minecraft 1.21.4", XssFilter.sanitize("Minecraft 1.21.4"));
    }

    @Test
    void sanitize_removesScriptTags() {
        String result = XssFilter.sanitize("<script>alert('xss')</script>");
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("alert"));
    }

    @Test
    void sanitize_removesScriptTagsCaseInsensitive() {
        String result = XssFilter.sanitize("<SCRIPT>alert(1)</SCRIPT>");
        assertFalse(result.toLowerCase().contains("<script>"));
    }

    @Test
    void sanitize_removesOnEventHandlers() {
        String result = XssFilter.sanitize("<img src=x onerror=\"alert(1)\">");
        assertFalse(result.contains("onerror"));
    }

    @Test
    void sanitize_removesOnclickWithSingleQuotes() {
        String result = XssFilter.sanitize("<div onclick='alert(1)'>click</div>");
        assertFalse(result.contains("onclick"));
    }

    @Test
    void sanitize_removesJavascriptProtocol() {
        String result = XssFilter.sanitize("javascript:alert(1)");
        assertFalse(result.toLowerCase().contains("javascript:"));
    }

    @Test
    void sanitize_removesIframe() {
        String result = XssFilter.sanitize("<iframe src=\"http://evil.com\"></iframe>");
        assertFalse(result.toLowerCase().contains("<iframe"));
    }

    @Test
    void sanitize_removesObjectAndEmbed() {
        String result1 = XssFilter.sanitize("<object data=\"evil.swf\"></object>");
        String result2 = XssFilter.sanitize("<embed src=\"evil.swf\"></embed>");
        assertFalse(result1.toLowerCase().contains("<object"));
        assertFalse(result2.toLowerCase().contains("<embed"));
    }

    @Test
    void sanitize_removesMetaAndLinkTags() {
        String result1 = XssFilter.sanitize("<meta http-equiv=\"refresh\" content=\"0;url=http://evil.com\">");
        String result2 = XssFilter.sanitize("<link rel=\"stylesheet\" href=\"http://evil.com/evil.css\">");
        assertFalse(result1.toLowerCase().contains("<meta"));
        assertFalse(result2.toLowerCase().contains("<link"));
    }

    @Test
    void sanitize_normalNickname_unchanged() {
        // 正常用户名的中文/英文/数字应原样保留
        assertEquals("speedrunner_99", XssFilter.sanitize("speedrunner_99"));
        assertEquals("玩家小明", XssFilter.sanitize("玩家小明"));
    }

    @Test
    void sanitize_emptyString_returnsEmpty() {
        assertEquals("", XssFilter.sanitize(""));
    }
}
