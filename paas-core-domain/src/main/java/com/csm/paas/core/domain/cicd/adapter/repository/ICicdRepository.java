package com.csm.paas.core.domain.cicd.adapter.repository;

import com.csm.paas.core.domain.cicd.model.entity.CicdEntity;

import java.util.List;
import java.util.Optional;

/**
 * `g_ci_cd` 的仓储入口（厂商 `dao/CicdMapper.java` + mybatis-dynamic-sql）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/CicdService.java` 的五个 mapper 调用点：
 * `selectByPrimaryKey`（`:126`、`:139`）、`deleteByPrimaryKey`（`:130`）、
 * `updateByPrimaryKey`（`:151`、`:417`、`:488`、`:512`）、`select(where...)`（`:213`、`:287`、`:302`、`:310`、`:430`）、
 * `insertSelective`（`:298`）、`updateByPrimaryKeySelective`（`:251`）。
 */
public interface ICicdRepository {

    /**
     * 厂商 `cicdMapper.selectByPrimaryKey(id)`（`CicdService:126`、`:139`、`:388` 那处是 `fileMapper`）。
     * <p>不存在 ⇒ `Optional.empty()`。
     */
    Optional<CicdEntity> queryById(Integer id);

    /**
     * 厂商 `CicdService.createTask:287` / `:302` / `:310` / `continuousIntegrationsV2:430` 的
     * `where image_name = ? and user_id = ?`。
     *
     * <p>照抄点：厂商用 `SqlBuilder.isEqualTo(userId)`——**userId 为 null 时生成 `user_id = null`，
     * 恒不命中**（不是"忽略该条件"）。本方法同样如此。
     */
    List<CicdEntity> queryByImageNameAndUserId(String imageName, String userId);

    /**
     * 厂商 `CicdService.cicdList:213` 的分页查询：
     * `where user_id = ? and image_name like ?`（`like` 仅在 keyword 非空时存在）
     * `order by create_at desc`，配合 `PageHelper.startPage(page,size)`。
     *
     * @param likeKeyword 已拼好的 `%keyword%`；**null 表示不过滤**
     */
    List<CicdEntity> queryPage(String userId, String likeKeyword, int limit, int offset);

    /**
     * 厂商 `((Page) cicdList).getTotal()`（`CicdService:277`）——PageHelper 对同一条 SQL 的 count 查询。
     * 过滤条件与 {@link #queryPage} 完全一致。
     */
    long countByUserAndKeyword(String userId, String likeKeyword);

    /** 厂商 `cicdMapper.insertSelective`（`CicdService:298`）：**只写非 null 列**，返回影响行数。 */
    int insertSelective(CicdEntity entity);

    /** 厂商 `cicdMapper.updateByPrimaryKey`（`CicdService:151`、`:417`、`:488`、`:512`）：**整行更新**。 */
    int updateByPrimaryKey(CicdEntity entity);

    /**
     * 厂商 `cicdMapper.updateByPrimaryKeySelective`（`CicdService:251`）：**只更新非 null 列**。
     * <p>本仓 DAO 用动态 SQL 实现"只更新非 null 列"，见 `ICicdDao.updateSelective`。
     */
    int updateByPrimaryKeySelective(CicdEntity entity);

    /** 厂商 `cicdMapper.deleteByPrimaryKey`（`CicdService:130`）。 */
    int deleteById(Integer id);
}
