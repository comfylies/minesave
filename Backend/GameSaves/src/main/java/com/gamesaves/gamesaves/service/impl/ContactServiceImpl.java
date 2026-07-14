package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.ContactCreateRequest;
import com.gamesaves.gamesaves.dto.response.ContactResponse;
import com.gamesaves.gamesaves.entity.ContactMessage;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.RateLimitException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ContactMessageRepository;
import com.gamesaves.gamesaves.service.ContactService;
import com.gamesaves.gamesaves.util.RateLimiter;
import com.gamesaves.gamesaves.util.XssFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
public class ContactServiceImpl implements ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactServiceImpl.class);

    private static final Set<String> VALID_CATEGORIES = Set.of("suggestion", "bug", "business", "other");

    private final ContactMessageRepository repository;
    private final RateLimiter rateLimiter;

    public ContactServiceImpl(ContactMessageRepository repository, RateLimiter rateLimiter) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public ContactResponse submit(ContactCreateRequest request, Long userId, String ip) {
        // 1. 验证类别
        if (!VALID_CATEGORIES.contains(request.getCategory())) {
            throw new BadRequestException("无效的留言类别：" + request.getCategory());
        }

        // 2. 速率限制：每IP每小时3次
        if (!rateLimiter.tryAcquireGlobal(ip, "contact", 3, 3600)) {
            log.warn("Contact rate limit hit: ip={}, userId={}", ip, userId);
            throw new RateLimitException("提交过于频繁，请稍后再试");
        }

        // 3. XSS 过滤（MD 语法不会被误杀，只移除危险 HTML）
        ContactMessage message = ContactMessage.builder()
                .userId(userId)
                .name(XssFilter.sanitize(request.getName().trim()))
                .email(XssFilter.sanitize(request.getEmail().trim().toLowerCase()))
                .category(XssFilter.sanitize(request.getCategory().trim()))
                .subject(XssFilter.sanitize(request.getSubject().trim()))
                .message(XssFilter.sanitize(request.getMessage()))
                .ipAddress(ip)
                .build();

        ContactMessage saved = repository.save(message);
        log.info("Contact message submitted: id={}, userId={}, category={}, subject={}",
                saved.getId(), userId, saved.getCategory(), saved.getSubject());

        return ContactResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContactResponse> listForAdmin(String status, Pageable pageable) {
        Page<ContactMessage> page;
        if (status != null && !status.isBlank()) {
            page = repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            page = repository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(ContactResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ContactResponse getById(Long id) {
        ContactMessage message = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", id));
        return ContactResponse.fromEntity(message);
    }

    @Override
    public ContactResponse resolve(Long id) {
        ContactMessage message = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", id));
        if (!"pending".equals(message.getStatus())) {
            throw new BadRequestException("只能标记待处理状态的留言为已解决");
        }
        message.setStatus("resolved");
        ContactMessage saved = repository.save(message);
        log.info("Contact message {} resolved", id);
        return ContactResponse.fromEntity(saved);
    }

    @Override
    public ContactResponse close(Long id) {
        ContactMessage message = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", id));
        message.setStatus("closed");
        ContactMessage saved = repository.save(message);
        log.info("Contact message {} closed", id);
        return ContactResponse.fromEntity(saved);
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ContactMessage", id);
        }
        repository.deleteById(id);
        log.info("Contact message {} deleted", id);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countByStatus("pending");
    }
}
