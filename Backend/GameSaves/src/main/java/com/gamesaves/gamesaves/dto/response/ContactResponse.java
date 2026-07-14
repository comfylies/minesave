package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.ContactMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactResponse {

    private Long id;
    private Long userId;
    private String name;
    private String email;
    private String category;
    private String subject;
    private String message;
    private String status;
    private String adminReply;
    private String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContactResponse fromEntity(ContactMessage m) {
        return ContactResponse.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .name(m.getName())
                .email(m.getEmail())
                .category(m.getCategory())
                .subject(m.getSubject())
                .message(m.getMessage())
                .status(m.getStatus())
                .adminReply(m.getAdminReply())
                .ipAddress(m.getIpAddress())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
