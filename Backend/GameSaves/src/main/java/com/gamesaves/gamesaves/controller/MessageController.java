package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.ConversationResponse;
import com.gamesaves.gamesaves.dto.response.DirectMessageResponse;
import com.gamesaves.gamesaves.dto.response.MessageEventResponse;
import com.gamesaves.gamesaves.service.DirectMessageService;
import com.gamesaves.gamesaves.service.MessageLongPollService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.async.DeferredResult;
import com.gamesaves.gamesaves.service.StorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/messages")
@SaCheckLogin
public class MessageController {

    private final DirectMessageService messageService;
    private final MessageLongPollService longPollService;
    private final StorageService storageService;

    public MessageController(DirectMessageService messageService, MessageLongPollService longPollService, StorageService storageService) {
        this.messageService = messageService;
        this.longPollService = longPollService;
        this.storageService = storageService;
    }

    @GetMapping("/conversations")
    public ApiResponse<PageDTO<ConversationResponse>> conversations(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(messageService.getConversations(StpUtil.getLoginIdAsLong(), page, size));
    }

    @GetMapping("/conversations/{id}/items")
    public ApiResponse<PageDTO<DirectMessageResponse>> items(@PathVariable Long id,
                                                               @RequestParam(required = false) Long beforeId,
                                                               @RequestParam(defaultValue = "40") int size) {
        return ApiResponse.success(messageService.getMessages(id, beforeId, size, StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/conversations/{targetUserId}/items")
    public ApiResponse<DirectMessageResponse> send(@PathVariable Long targetUserId,
                                                    @RequestParam(required = false) String content,
                                                    @RequestParam(required = false) MultipartFile image,
                                                    HttpServletRequest request) {
        return ApiResponse.success(messageService.send(targetUserId, content, image,
                StpUtil.getLoginIdAsLong(), request.getRemoteAddr()));
    }

    @PostMapping("/conversations/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.success(null);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.success(messageService.getUnreadCount(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/events")
    public DeferredResult<ApiResponse<MessageEventResponse>> events(@RequestParam String clientId,
                                                                      @RequestParam(required = false) String epoch,
                                                                      @RequestParam(required = false) Long cursor) {
        return longPollService.awaitEvent(StpUtil.getLoginIdAsLong(), clientId, epoch, cursor);
    }

    @GetMapping("/items/{messageId}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long messageId, @RequestParam(defaultValue = "true") boolean thumbnail) {
        String key = messageService.getImageKey(messageId, thumbnail, StpUtil.getLoginIdAsLong());
        MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG : key.endsWith(".webp")
                ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(storageService.read(key));
    }
}
