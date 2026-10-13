package com.csm.paas.core.domain.user.adapter.repository;

import com.csm.paas.core.domain.user.model.entity.UserEntity;

import com.csm.paas.core.domain.user.model.valobj.UserLoginItemVO;

import java.util.List;
import java.util.Optional;

/** 用户仓储（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface IUserRepository {

    Optional<UserEntity> queryByUsername(String username);

    /** 登录页账号下拉用（厂商 GET /v1/user/get，**公开**）。 */
    List<UserLoginItemVO> queryLoginList();

    long count();
}
