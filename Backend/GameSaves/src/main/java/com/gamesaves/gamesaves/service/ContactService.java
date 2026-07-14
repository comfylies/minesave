package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.ContactCreateRequest;
import com.gamesaves.gamesaves.dto.response.ContactResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContactService {

    ContactResponse submit(ContactCreateRequest request, Long userId, String ip);

    Page<ContactResponse> listForAdmin(String status, Pageable pageable);

    ContactResponse getById(Long id);

    ContactResponse resolve(Long id);

    ContactResponse close(Long id);

    void delete(Long id);

    long countPending();
}
