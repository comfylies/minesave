package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class DirectoryBrowseResponse {

    private String currentPath;
    private List<FileEntryResponse> files;
    private List<FileEntryResponse> directories;
    private List<BreadcrumbEntry> breadcrumbs;

    @Data
    @Builder
    @AllArgsConstructor
    public static class BreadcrumbEntry {
        private String name;
        private String path;
    }
}
