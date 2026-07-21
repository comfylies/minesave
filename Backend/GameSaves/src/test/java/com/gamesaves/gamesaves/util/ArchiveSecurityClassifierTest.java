package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.SavingItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchiveSecurityClassifierTest {

    @Test
    void classifiesOrdinarySaveFilesAsSafe() {
        Article.SecurityLevel level = ArchiveSecurityClassifier.classify(List.of(
                SavingItem.builder().virtualPath("world/level.dat").fileType("dat").build(),
                SavingItem.builder().virtualPath("config/options.json").fileType("json").build()
        ));

        assertEquals(Article.SecurityLevel.SAFE, level);
    }

    @Test
    void classifiesExecutableOrScriptFilesAsWarning() {
        Article.SecurityLevel level = ArchiveSecurityClassifier.classify(List.of(
                SavingItem.builder().virtualPath("launcher/start.sh").fileType("sh").build(),
                SavingItem.builder().virtualPath("launcher/game.exe").fileType("exe").build()
        ));

        assertEquals(Article.SecurityLevel.WARNING, level);
    }
}
