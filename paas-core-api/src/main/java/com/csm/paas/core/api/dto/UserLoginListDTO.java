package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 厂商 GET /v1/user/get 的 data 形状：{list, total}。 */
@Data
@Builder
public class UserLoginListDTO {
    private List<UserLoginItemDTO> list;
    private long total;
}
