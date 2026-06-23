package com.gamesaves.gamesaves.util;

import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Markdown to HTML renderer.
 * For production use, consider adding commonmark-java dependency.
 */
@UtilityClass
public class MarkdownRenderer {

    private static final Logger log = LoggerFactory.getLogger(MarkdownRenderer.class);

    public static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeBlockContent = new StringBuilder();
        boolean inList = false;
        String listType = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Code blocks
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    appendCodeBlock(html, codeBlockContent.toString());
                    codeBlockContent = new StringBuilder();
                    inCodeBlock = false;
                } else {
                    // Extract language tag: ```java → java
                    String lang = line.trim().substring(3).trim();
                    if (!lang.isEmpty()) {
                        codeBlockContent.append("__LANG__").append(lang).append("__LANG__\n");
                    }
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                if (!codeBlockContent.isEmpty()) codeBlockContent.append("\n");
                codeBlockContent.append(line);
                continue;
            }

            // Headings
            if (line.startsWith("### ")) {
                closeList(html, inList, listType);
                inList = false;
                html.append("<h3>").append(renderInline(line.substring(4))).append("</h3>\n");
            } else if (line.startsWith("## ")) {
                closeList(html, inList, listType);
                inList = false;
                html.append("<h2>").append(renderInline(line.substring(3))).append("</h2>\n");
            } else if (line.startsWith("# ")) {
                closeList(html, inList, listType);
                inList = false;
                html.append("<h1>").append(renderInline(line.substring(2))).append("</h1>\n");
            }
            // Unordered lists
            else if (line.trim().matches("^[-*+]\\s.*")) {
                if (!inList || !"ul".equals(listType)) {
                    closeList(html, inList, listType);
                    html.append("<ul>\n");
                    inList = true;
                    listType = "ul";
                }
                html.append("<li>").append(renderInline(line.trim().substring(2))).append("</li>\n");
            }
            // Ordered lists
            else if (line.trim().matches("^\\d+\\.\\s.*")) {
                if (!inList || !"ol".equals(listType)) {
                    closeList(html, inList, listType);
                    html.append("<ol>\n");
                    inList = true;
                    listType = "ol";
                }
                String text = line.trim().replaceFirst("^\\d+\\.\\s", "");
                html.append("<li>").append(renderInline(text)).append("</li>\n");
            }
            // Horizontal rule
            else if (line.trim().matches("^[-*_]{3,}$")) {
                closeList(html, inList, listType);
                inList = false;
                html.append("<hr>\n");
            }
            // Paragraphs (skip empty lines)
            else if (line.trim().isEmpty()) {
                closeList(html, inList, listType);
                inList = false;
            }
            // Regular paragraph
            else {
                closeList(html, inList, listType);
                inList = false;
                html.append("<p>").append(renderInline(line)).append("</p>\n");
            }
        }

        closeList(html, inList, listType);

        // Close unclosed code block
        if (inCodeBlock) {
            appendCodeBlock(html, codeBlockContent.toString());
        }

        return html.toString();
    }

    private static void appendCodeBlock(StringBuilder html, String content) {
        String lang = "";
        String code = content;
        // Extract language marker
        if (code.startsWith("__LANG__")) {
            int end = code.indexOf("__LANG__", 8);
            if (end > 0) {
                lang = code.substring(8, end);
                code = code.substring(end + 8);
                if (code.startsWith("\n")) code = code.substring(1);
            }
        }
        String langClass = lang.isEmpty() ? "" : " class=\"language-" + escapeHtmlAttr(lang) + "\"";
        html.append("<pre><code").append(langClass).append(">")
                .append(escapeHtml(code))
                .append("</code></pre>\n");
    }

    private static void closeList(StringBuilder html, boolean inList, String listType) {
        if (inList && "ul".equals(listType)) html.append("</ul>\n");
        if (inList && "ol".equals(listType)) html.append("</ol>\n");
    }

    private static String renderInline(String text) {
        if (text == null) return "";
        // Bold: **text** or __text__
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("__(.+?)__", "<strong>$1</strong>");
        // Italic: *text* or _text_
        text = text.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        text = text.replaceAll("_(.+?)_", "<em>$1</em>");
        // Inline code: `text`
        text = text.replaceAll("`(.+?)`", "<code>$1</code>");
        // Images: ![alt](url) — must be BEFORE links, otherwise link regex steals the [alt](url) part
        text = text.replaceAll("!\\[(.*?)\\]\\((.+?)\\)", "<img src=\"$2\" alt=\"$1\">");
        // Links: [text](url)
        text = text.replaceAll("\\[(.+?)\\]\\((.+?)\\)", "<a href=\"$2\">$1</a>");
        // Strikethrough: ~~text~~
        text = text.replaceAll("~~(.+?)~~", "<del>$1</del>");

        return text;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeHtmlAttr(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
