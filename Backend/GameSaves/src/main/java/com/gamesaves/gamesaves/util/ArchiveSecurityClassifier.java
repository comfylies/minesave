package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.SavingItem;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Assigns a download warning level without blocking archives that legitimately contain tools or scripts. */
public final class ArchiveSecurityClassifier {

    private static final Set<String> EXECUTABLE_OR_SCRIPT_EXTENSIONS = Set.of(
            "exe", "dll", "sys", "msi", "com", "scr", "bat", "cmd", "ps1",
            "sh", "bash", "zsh", "fish", "py", "rb", "pl", "php", "js", "vbs", "jar"
    );

    private ArchiveSecurityClassifier() {
    }

    public static Article.SecurityLevel classify(List<SavingItem> items) {
        boolean containsExecutableOrScript = items.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDirectory()))
                .anyMatch(item -> EXECUTABLE_OR_SCRIPT_EXTENSIONS.contains(extensionOf(item)));
        return containsExecutableOrScript ? Article.SecurityLevel.WARNING : Article.SecurityLevel.SAFE;
    }

    private static String extensionOf(SavingItem item) {
        if (item.getFileType() != null && !item.getFileType().isBlank()) {
            return item.getFileType().toLowerCase(Locale.ROOT);
        }
        return ArchiveUtils.getExtension(item.getVirtualPath()).toLowerCase(Locale.ROOT);
    }
}
