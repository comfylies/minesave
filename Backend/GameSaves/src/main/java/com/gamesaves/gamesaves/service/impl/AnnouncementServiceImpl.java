package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.AnnouncementCreateRequest;
import com.gamesaves.gamesaves.dto.response.AnnouncementResponse;
import com.gamesaves.gamesaves.entity.Announcement;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.AnnouncementRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.AnnouncementService;
import com.gamesaves.gamesaves.util.MarkdownRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementServiceImpl.class);

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository,
                                   UserRepository userRepository) {
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AnnouncementResponse create(AnnouncementCreateRequest request, Long authorId) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", authorId));

        String contentHtml = MarkdownRenderer.render(request.getContentRaw());

        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .contentRaw(request.getContentRaw())
                .contentHtml(contentHtml)
                .author(author)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement created: id={}, title={}, author={}", saved.getId(), saved.getTitle(), author.getUsername());
        return AnnouncementResponse.fromEntity(saved);
    }

    @Override
    public AnnouncementResponse update(Long id, AnnouncementCreateRequest request) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", id));

        announcement.setTitle(request.getTitle());
        announcement.setContentRaw(request.getContentRaw());
        announcement.setContentHtml(MarkdownRenderer.render(request.getContentRaw()));
        if (request.getIsActive() != null) {
            announcement.setIsActive(request.getIsActive());
        }

        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement updated: id={}", saved.getId());
        return AnnouncementResponse.fromEntity(saved);
    }

    @Override
    public void delete(Long id) {
        if (!announcementRepository.existsById(id)) {
            throw new ResourceNotFoundException("Announcement", id);
        }
        announcementRepository.deleteById(id);
        log.info("Announcement deleted: id={}", id);
    }

    @Override
    public AnnouncementResponse toggleActive(Long id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", id));

        announcement.setIsActive(!announcement.getIsActive());
        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement toggled: id={}, isActive={}", saved.getId(), saved.getIsActive());
        return AnnouncementResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listAll() {
        return announcementRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AnnouncementResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listActive() {
        return announcementRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(AnnouncementResponse::fromEntity)
                .toList();
    }
}
