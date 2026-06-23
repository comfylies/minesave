package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.TagCreateRequest;
import com.gamesaves.gamesaves.dto.response.TagResponse;
import com.gamesaves.gamesaves.entity.Tag;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.TagRepository;
import com.gamesaves.gamesaves.service.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TagServiceImpl implements TagService {

    private static final Logger log = LoggerFactory.getLogger(TagServiceImpl.class);

    private final TagRepository tagRepository;
    private final ArticleRepository articleRepository;

    public TagServiceImpl(TagRepository tagRepository, ArticleRepository articleRepository) {
        this.tagRepository = tagRepository;
        this.articleRepository = articleRepository;
    }

    @Override
    public TagResponse createTag(TagCreateRequest request) {
        if (tagRepository.existsByName(request.getName())) {
            throw new BadRequestException("Tag already exists: " + request.getName());
        }

        Tag.Source source = "admin".equalsIgnoreCase(request.getSource())
                ? Tag.Source.admin : Tag.Source.user;

        Tag tag = Tag.builder()
                .name(request.getName())
                .source(source)
                .build();

        tag = tagRepository.save(tag);
        log.info("Tag created: {} (source={})", tag.getName(), tag.getSource());
        return TagResponse.fromEntity(tag);
    }

    @Override
    public TagResponse updateTag(Long id, TagCreateRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", id));

        if (!tag.getName().equals(request.getName()) && tagRepository.existsByName(request.getName())) {
            throw new BadRequestException("Tag name already exists: " + request.getName());
        }

        tag.setName(request.getName());
        if (request.getSource() != null) {
            tag.setSource("admin".equalsIgnoreCase(request.getSource())
                    ? Tag.Source.admin : Tag.Source.user);
        }

        tag = tagRepository.save(tag);
        log.info("Tag updated: id={}, name={}", id, tag.getName());
        return TagResponse.fromEntity(tag);
    }

    @Override
    public void deleteTag(Long id) {
        if (!tagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tag", id);
        }
        try {
            tagRepository.deleteById(id);
            log.info("Tag deleted: id={}", id);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("Cannot delete tag: it is still associated with articles");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags() {
        return tagRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TagResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getTagsBySource(String source) {
        try {
            Tag.Source sourceEnum = Tag.Source.valueOf(source.toLowerCase());
            return tagRepository.findBySourceOrderByCreatedAtDesc(sourceEnum).stream()
                    .map(TagResponse::fromEntity)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid source value: " + source + " (must be 'admin' or 'user')");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> searchTags(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllTags();
        }
        return tagRepository.findByNameContaining(keyword).stream()
                .map(TagResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
