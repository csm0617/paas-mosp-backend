package com.csm.paas.core.domain.subrole.service;

import com.csm.paas.core.domain.subrole.model.entity.SubRoleListEntity;

/** 子角色查询服务（切片 3c：子角色列表）。 */
public interface ISubRoleQueryService {

    /**
     * 子角色列表（厂商 `GET /v1/sub_role/switchRole`）。
     *
     * @param page 已归一化页码
     * @param size 已归一化每页条数
     * @param subRoleName 子角色名关键字（调用方已去空格；可为 null）
     */
    SubRoleListEntity querySubRoleList(int page, int size, String subRoleName);
    /**
     * 子角色详情（厂商 `GET /v1/sub_role/{id}` → `SubRoleController.subRoleDetail:163-174` +
     * `SubRoleServiceImpl.detail:341-344`）。
     *
     * <p>纯按主键取；`nameUs`/`nameFr` 用 i18n 自动名覆盖、**取不到就兜底成 `name`**
     * （厂商 `getAutoNameMessage` 在本环境无覆盖数据 ⇒ 等价于兜底）；
     * `userList`/`roleList` **不填**（厂商 `detail` 只 select 主键 ⇒ 两个键为 null）。
     * 查不到 ⇒ 返回 null（HTTP 200 + `data:null`）。
     */
    com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity querySubRoleDetail(String id);

    /**
     * 导出用行数据（厂商 `generateExportExcel:470-512`，规格见 `docs/findings/154`）。
     *
     * <p>`ids` 为空/null ⇒ **全量**（厂商 `isInWhenPresent` 空集合不加 where），按 `created_at DESC`。
     * 每行的用户/战位列是**用户名列表**与**战位 key 列表**（由调用方序列化成 JSON 数组写进单元格）。
     * `nameUs`/`nameFr` 走 i18n 自动名覆盖——本环境无覆盖数据 ⇒ **null**（单元格留空）。
     */
    java.util.List<com.csm.paas.core.domain.subrole.model.valobj.SubRoleExportRowVO> queryExportRows(
            java.util.List<String> ids);
}
