package com.gamesaves.gamesaves.util;

import lombok.experimental.UtilityClass;

/**
 * Simple XSS filter that strips dangerous HTML tags and attributes.
 * For production use, consider OWASP Java HTML Sanitizer.
 */
@UtilityClass
public class XssFilter {

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        return input
                .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                .replaceAll("(?i)<[^>]*on\\w+\\s*=\\s*\"[^\"]*\"[^>]*>", "")
                .replaceAll("(?i)<[^>]*on\\w+\\s*=\\s*'[^']*'[^>]*>", "")
                .replaceAll("(?i)<[^>]*on\\w+\\s*=[^>]*>", "")
                .replaceAll("(?i)javascript\\s*:", "")
                .replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "")
                .replaceAll("(?i)<object[^>]*>.*?</object>", "")
                .replaceAll("(?i)<embed[^>]*>.*?</embed>", "")
                .replaceAll("(?i)<link[^>]*>", "")
                .replaceAll("(?i)<meta[^>]*>", "")
                .trim();
    }
}
