package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 {@code GET /v1/user/info} 响应体（{@code UserInfoResponse}）：{@code {"list":[…],"total":n}}。
 * <p>实测样本 {@code docs/findings/runtime-samples/v1_user_info.json}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoListResponseDTO {

    private List<UserInfoItemDTO> list;
    private long total;
}
