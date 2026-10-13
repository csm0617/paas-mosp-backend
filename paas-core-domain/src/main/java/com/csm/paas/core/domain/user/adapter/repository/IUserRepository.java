package com.csm.paas.core.domain.user.adapter.repository;

import com.csm.paas.core.domain.user.model.entity.UserEntity;

import java.util.Optional;

/** 用户仓储（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface IUserRepository {

    Optional<UserEntity> queryByUsername(String username);
}
