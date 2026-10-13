package com.csm.paas.core.api.dto;

import lombok.Data;

/** 厂商 LoginForm 的形状：{username, password, role}，`role` 是**战位 key**。 */
@Data
public class LoginRequestDTO {
    private String username;
    private String password;
    private String role;
}
