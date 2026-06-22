package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.SavingItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class FileEntryResponse {

    private String name;            // last segment of virtual_path
    private String virtualPath;     // full virtual path
    private String physicalKey;
    private Long fileSize;
    private String md5Hash;
    private String fileType;
    private Boolean isText;
    private Boolean isDirectory;

    public static FileEntryResponse fromEntity(SavingItem item) {
        String name = item.getVirtualPath();
        if (item.getIsDirectory() && name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        return FileEntryResponse.builder()
                .name(name)
                .virtualPath(item.getVirtualPath())
                .physicalKey(item.getPhysicalKey())
                .fileSize(item.getFileSize())
                .md5Hash(item.getMd5Hash())
                .fileType(item.getFileType())
                .isText(item.getIsText())
                .isDirectory(item.getIsDirectory())
                .build();
    }
}
