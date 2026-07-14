package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactCreateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 100, message = "姓名最长100个字符")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 200, message = "邮箱最长200个字符")
    private String email;

    @NotBlank(message = "请选择类别")
    @Size(max = 50, message = "类别最长50个字符")
    private String category;

    @NotBlank(message = "主题不能为空")
    @Size(max = 200, message = "主题最长200个字符")
    private String subject;

    @NotBlank(message = "留言内容不能为空")
    private String message;
}
