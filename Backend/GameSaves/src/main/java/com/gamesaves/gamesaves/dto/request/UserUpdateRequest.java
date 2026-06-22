package com.gamesaves.gamesaves.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private String nickname;

    private String phone;

    private String email;

    private String bio;

    private String avatarUrl;
}
