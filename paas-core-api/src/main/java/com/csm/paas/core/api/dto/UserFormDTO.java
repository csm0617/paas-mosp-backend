package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 厂商 `POST /v1/user` / `PUT /v1/user/{userId}` 的请求体（`UserForm`）。
 * <p>`birth` 是 `java.util.Date`：**请求必须是 epoch 毫秒数字**（发 `"2000-01-02"` 这类 date-only 串，
 * 厂商会 500/-1），实测见 `docs/findings/30` 末节。
 * <p>三个列表字段：`roleListKey`=**战位** key、`userListKey`=**角色** key、`subRoleListId`=子角色 **id**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserFormDTO {

    private String username;
    private String nickname;
    private String password;
    private Integer status;
    private String email;
    private Date birth;
    private String phone;
    private String desc;
    private String avatar;
    private List<String> roleListKey;
    private List<String> userListKey;
    private List<String> subRoleListId;
}
