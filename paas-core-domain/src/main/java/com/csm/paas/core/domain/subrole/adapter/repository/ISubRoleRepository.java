package com.csm.paas.core.domain.subrole.adapter.repository;

import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleRoleVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleUserVO;

import java.util.List;

/** 子角色仓储（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface ISubRoleRepository {

    /** 全量按 created_at 倒序分页（厂商 `SubRoleServiceImpl.java:188-191` 的空关键字分支）。 */
    List<SubRoleEntity> queryPageOrderByCreatedAtDesc(int offset, int size);

    long count();

    /** 按名称模糊（厂商 `:194` 的 `LIKE` 分支；该分支**不分页**）。 */
    List<SubRoleEntity> queryByNameLike(String likeKeyword);

    /**
     * 某用户关联的子角色（厂商 `SubRoleService.listByUserId`，`SubRoleServiceImpl.java:305-316`）：
     * 按 `g_user_sub_role` 逐条取子角色，**不填 `userList`/`roleList`**（所以那两个键在 JSON 里是 null）。
     */
    List<SubRoleEntity> queryByUserId(String userId);

    /** 子角色关联的用户（厂商 `getRoleUser`，`SubRoleServiceImpl.java:206-217`）。 */
    List<SubRoleUserVO> queryUsersBySubRoleId(String subRoleId);

    /** 子角色关联的战位（厂商 `getRoleRole`，`SubRoleServiceImpl.java:219-230`）。 */
    List<SubRoleRoleVO> queryRolesBySubRoleId(String subRoleId);

    /**
     * 按 id 批量取子角色（厂商 `getSubRoleListByRoleId`，`SubRoleServiceImpl.java:385-397`）。
     * <p><b>厂商用 `HashSet` 去重，返回顺序不确定</b>；这里按传入 id 的顺序返回，属"更确定"的偏离
     * （多元素时无法与厂商逐字节对齐，见 `docs/findings/28-e2e-role-page.md`）。
     */
    List<SubRoleEntity> queryByIds(List<String> ids);

    /**
     * 按 `key` 取子角色（厂商在用户导入里逐个 `subRoleMapper.select(key = ?)`，`UserService.java:1222,1291`；
     * 查不到 ⇒ 调用方跳过）。
     */
    java.util.Optional<SubRoleEntity> queryByKey(String key);

    /** 导出用：按 id 集合取（**空集合 ⇒ 全量**），按 `created_at DESC`。 */
    List<SubRoleEntity> queryForExport(List<String> ids);

    /** 该子角色关联用户的用户名列表。 */
    List<String> queryUsernamesBySubRoleId(String subRoleId);

    /** 该子角色关联战位的 key 列表。 */
    List<String> queryRoleKeysBySubRoleId(String subRoleId);

    /** 按主键取子角色（厂商 `SubRoleServiceImpl.detail:341-344`）。 */
    java.util.Optional<SubRoleEntity> queryById(String id);

    /** 删子角色（厂商 `deleteByIds` 的 `deleteByPrimaryKey`）。 */
    void deleteById(String id);

    /** 删该子角色的"子角色-战位"关联。 */
    void deleteSubRoleRolesBySubRoleId(String subRoleId);

    /** 删该子角色的"用户-子角色"关联。 */
    void deleteUserSubRolesBySubRoleId(String subRoleId);

    /** 新增时的重名检查（**查的是 `g_role`**，厂商如此）。 */
    boolean existsInRoleByKeyOrName(String key, String name);

    /** 修改时 key 是否与**别的**子角色重复。 */
    boolean existsKeyExcludingId(String key, String id);

    /** 修改时 name 是否与**别的**子角色重复。 */
    boolean existsNameExcludingId(String name, String id);

    /** 插子角色。 */
    void insertSubRole(String id, String key, String name, String desc, long updatedAt, long createdAt);

    /** 选择性更新（只写非 null 字段；`desc` 传 null 时**不写该列**）。 */
    int updateSubRoleSelective(String id, String key, String name, String desc, long updatedAt);

    /** 删该**用户**的全部"用户-子角色"关联（厂商 `usersRelateRole:145` / `userAddRole:348`）。 */
    void deleteUserSubRolesByUserId(String userId);

    /** 插一条"用户-子角色"关联（厂商 `usersRelateRole:150-157` / `userAddRole:350-356` / `roleAddUser:373-379`）。 */
    void insertUserSubRole(String id, String userId, String subRoleId, long createdAt, long updatedAt);

    /** 该用户是否存在（厂商 `usersRelateRole` 里对每个 userId `selectByPrimaryKey`，查不到就跳过）。 */
    boolean userExists(String userId);

    /**
     * 只改 `desc`（厂商 `importSubroleExcel:614-616`：`subrole.setDesc(desc); updateByPrimaryKey(subrole)`）。
     * <p>`desc` 允许为 **null**（表格里读不到 ⇒ 写成 NULL）。
     */
    void updateDesc(String id, String desc);

    /**
     * 用给定用户 id 列表**替换**该子角色的用户关联（厂商 `usersRelateRole:144-160` 的等价写法）：
     * 先删光该子角色的 `g_user_sub_role`，再逐个插入（**不存在的用户跳过**）。
     * <p><b>刻意不照抄的一处</b>：厂商还会把每个用户的 `created_at` 覆盖成 now（写错字段），
     * 本域统一不做（见 `docs/deviations.md` B27）。
     */
    void replaceUserSubRoleRelations(String subRoleId, java.util.List<String> userIds);

    /**
     * 用给定战位 id 列表**替换**该子角色的"子角色-战位"关联
     * （厂商 `subRoleRelateRoles:410-419`：先删 `g_sub_role_role` 再逐个插）。
     * <p><b>刻意不照抄的一处</b>：厂商那段 `delete` 写错了表（删的是 `g_user_sub_role`，
     * 会把上一步刚写好的用户关联清掉），这里按**意图**删 `g_sub_role_role`（见 `deviations.md` B28）。
     */
    void replaceSubRoleRoleRelations(String subRoleId, java.util.List<String> roleIds);
}
