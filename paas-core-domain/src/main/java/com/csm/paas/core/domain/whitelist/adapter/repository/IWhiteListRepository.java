package com.csm.paas.core.domain.whitelist.adapter.repository;

import com.csm.paas.core.domain.whitelist.model.entity.WhiteListEntity;

import java.util.List;
import java.util.Optional;

/** 白名单仓储（`g_white_list`）。 */
public interface IWhiteListRepository {

    /**
     * 分页列表（厂商 `WhiteListService.list:36-44`）：`path LIKE %keyword%`（keyword 为 null 时**不加条件**）、
     * `created_at DESC`。
     */
    List<WhiteListEntity> queryPage(String likeKeyword, int offset, int size);

    long count(String likeKeyword);

    /** 按 **path 精确**取一条（厂商 `addPath` 的查重与回查都是 `path = ?`）。 */
    Optional<WhiteListEntity> queryByPath(String path);

    Optional<WhiteListEntity> queryById(long id);

    void insert(String path, long createdAt);

    void deleteById(long id);
}
