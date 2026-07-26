package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.TagCreateRequest;
import com.gamesaves.gamesaves.dto.response.TagResponse;

import java.util.List;
import java.util.Collection;

public interface TagService {

    TagResponse createTag(TagCreateRequest request);

    TagResponse updateTag(Long id, TagCreateRequest request);

    void deleteTag(Long id);

    List<TagResponse> getAllTags();

    List<TagResponse> getTagsBySource(String source);

    List<TagResponse> searchTags(String keyword);

    List<TagResponse> getFeaturedTags();

    List<TagResponse> getTagsByIds(Collection<Long> ids);
}
