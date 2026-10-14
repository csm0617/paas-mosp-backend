package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.user.adapter.port.ITenxUserListPort;
import com.csm.paas.core.domain.user.model.entity.TenxUserEntity;
import com.csm.paas.core.domain.user.service.ITenxUserQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * `GET /v1/user/tenx_user`（厂商 `UserService.getTenxUserList:1584-1618`），逐行照抄。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenxUserQueryServiceImpl implements ITenxUserQueryService {

    private final ITenxUserListPort tenxUserListPort;

    @Override
    public List<TenxUserEntity> queryTenxUserList(String userName) {
        List<TenxUserEntity> users;
        try {
            users = tenxUserListPort.allPageUser();
        } catch (Exception e) {
            // 厂商 :1591-1594：拉取失败 ⇒ 400 `service.get.container.admin.info.failed`
            log.error("获取容器后台管理或流水线用户信息失败", e);
            throw new WrongArgumentException("service.get.container.admin.info.failed");
        }
        ArrayList<TenxUserEntity> list = new ArrayList<>();
        if (users == null || users.isEmpty()) {
            // 厂商 :1595-1597：空 ⇒ 直接返回空列表
            return list;
        }
        for (TenxUserEntity u : users) {
            list.add(TenxUserEntity.builder().userName(u.getUserName()).apiToken(u.getApiToken()).build());
        }
        ArrayList<TenxUserEntity> findUserInfo = new ArrayList<>();
        if (userName != null) {
            // 厂商 :1604-1612：**精确等值**过滤（不是模糊匹配）
            for (TenxUserEntity tenxUserInfo : list) {
                if (!userName.equals(tenxUserInfo.getUserName())) {
                    continue;
                }
                findUserInfo.add(TenxUserEntity.builder()
                        .userName(tenxUserInfo.getUserName())
                        .apiToken(tenxUserInfo.getApiToken())
                        .build());
            }
            return findUserInfo;
        }
        return list;
    }
}
