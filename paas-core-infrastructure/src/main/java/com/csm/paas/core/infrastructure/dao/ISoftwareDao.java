package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.SoftwarePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 软件目录（K2，`g_software`）。 */
@Mapper
public interface ISoftwareDao {

    /**
     * 软件列表。排序**恒为 `order_top DESC` 在前**，其后按 `order` 指定的字段（厂商 `SoftwareService.java:487-493`）。
     * <p>用 `<choose>` 白名单化排序列，避免把用户输入拼进 ORDER BY。
     */
    @Select("""
            <script>
            SELECT id, `key`, name, app_name, router_key, type, version, created_at, updated_at,
                   framework, exec_name, location, remote_ip, remote_name, remote_password,
                   active_create, active_expire, order_top, order_time, `desc`, active_code, screenshot, icon
            FROM g_software
            <where>
              <if test="likeName != null">
                (name LIKE #{likeName} OR `key` LIKE #{likeName})
              </if>
              <if test="type != null">AND type = #{type}</if>
            </where>
            ORDER BY order_top DESC,
            <choose>
              <when test="order == 'name'">name</when>
              <when test="order == 'createdAt'">created_at</when>
              <when test="order == 'updatedAt'">updated_at</when>
              <otherwise>`key`</otherwise>
            </choose>
            <choose><when test="asc">ASC</when><otherwise>DESC</otherwise></choose>
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<SoftwarePO> selectPage(@Param("likeName") String likeName, @Param("type") String type,
                                @Param("order") String order, @Param("asc") boolean asc,
                                @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM g_software
            <where>
              <if test="likeName != null">
                (name LIKE #{likeName} OR `key` LIKE #{likeName})
              </if>
              <if test="type != null">AND type = #{type}</if>
            </where>
            </script>
            """)
    long selectCount(@Param("likeName") String likeName, @Param("type") String type);

    /** 白名单里是否存在该路径（厂商 `softWareReleaseStatus`）。 */
    @Select("SELECT COUNT(*) FROM g_white_list WHERE path = #{path}")
    long countWhiteListPath(@Param("path") String path);

    // ---------- 写（新增）----------

    @Select("SELECT COUNT(*) FROM g_software WHERE `key` = #{key}")
    long countByKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM g_software WHERE name = #{name}")
    long countByName(@Param("name") String name);

    @Insert("""
            INSERT INTO g_software (id, `key`, name, app_name, router_key, type, version, created_at, updated_at,
                                    framework, exec_name, location, remote_ip, remote_name, remote_password,
                                    order_top, `desc`, screenshot, icon)
            VALUES (#{id}, #{key}, #{name}, #{appName}, #{routerKey}, #{type}, #{version}, #{createdAt}, #{updatedAt},
                    #{framework}, #{execName}, #{location}, #{remoteIp}, #{remoteName}, #{remotePassword},
                    #{orderTop}, #{desc}, #{screenshot}, #{icon})
            """)
    int insert(SoftwarePO software);

    @Select("SELECT COUNT(*) FROM g_software WHERE `key` = #{key} AND id <> #{id}")
    long countByKeyExcludingId(@Param("key") String key, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM g_software WHERE name = #{name} AND id <> #{id}")
    long countByNameExcludingId(@Param("name") String name, @Param("id") String id);

    /**
     * 整行更新（厂商 `updateByPrimaryKey`）。注意**不更新 `created_at`/`updated_at`**——
     * 厂商的 `editSoftWare` 只 `copyProperties` 表单字段，时间戳保持原值（与"新增只设 createdAt"是同一种粗糙）。
     */
    @Update("""
            UPDATE g_software SET `key` = #{key}, name = #{name}, app_name = #{appName}, router_key = #{routerKey},
                                  type = #{type}, version = #{version}, framework = #{framework},
                                  exec_name = #{execName}, location = #{location},
                                  remote_ip = #{remoteIp}, remote_name = #{remoteName}, remote_password = #{remotePassword},
                                  active_create = #{activeCreate}, active_expire = #{activeExpire},
                                  order_top = #{orderTop}, order_time = #{orderTime}, `desc` = #{desc},
                                  active_code = #{activeCode}, screenshot = #{screenshot}, icon = #{icon}
            WHERE id = #{id}
            """)
    int updateByPrimaryKey(SoftwarePO software);

    /** 该战位已关联的软件数（厂商用它算下一个 `order`）。 */
    @Select("SELECT COUNT(*) FROM g_role_software WHERE role_id = #{roleId}")
    long countRelationByRoleId(@Param("roleId") String roleId);

    @Select("SELECT COUNT(*) FROM g_role_software WHERE role_id = #{roleId} AND app_id = #{softwareId}")
    long countRelation(@Param("roleId") String roleId, @Param("softwareId") String softwareId);

    @Insert("""
            INSERT INTO g_role_software (role_id, app_id, app_name, `order`, created_at)
            VALUES (#{roleId}, #{softwareId}, #{appName}, #{order}, #{createdAt})
            """)
    int insertRelation(@Param("roleId") String roleId, @Param("softwareId") String softwareId,
                       @Param("appName") String appName, @Param("order") int order,
                       @Param("createdAt") long createdAt);

    /** 软件关联的战位（厂商 `RoleMapper.getRelationRole`：`select id,name,key from g_role where id in (...)`）。 */
    @Select("""
            <script>
            SELECT id, name, `key` FROM g_role
            WHERE id IN <foreach item='item' collection='roleIds' open='(' separator=',' close=')'>#{item}</foreach>
            </script>
            """)
    List<com.csm.paas.core.infrastructure.dao.po.RelationRolePO> selectRelationRoles(@Param("roleIds") List<String> roleIds);

    // ---------- 删除（厂商 removeSoftware，:1520-1533）----------

    @org.apache.ibatis.annotations.Delete("DELETE FROM g_role_software WHERE app_id = #{softwareId}")
    int deleteRoleSoftwareBySoftwareId(@Param("softwareId") String softwareId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM g_software_app WHERE software_id = #{softwareId}")
    int deleteSoftwareAppBySoftwareId(@Param("softwareId") String softwareId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM g_service_port WHERE software_id = #{softwareId}")
    int deleteServicePortBySoftwareId(@Param("softwareId") String softwareId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM g_software WHERE id = #{softwareId}")
    int deleteById(@Param("softwareId") String softwareId);

    /**
     * 软件当前版本对应的上传记录（厂商 `getCurrentSoftwareVersionInfo`）：
     * `left join g_software s on ur.id = s.version where s.id = ?`。
     */
    @Select("""
            SELECT ur.id, ur.real_store_filename, ur.exec_file_name, ur.updated_at,
                   ur.exec_fault_message, ur.status, ur.relation_software_id
            FROM g_upload_store_record ur
            LEFT JOIN g_software s ON ur.id = s.version
            WHERE s.id = #{softwareId}
            """)
    com.csm.paas.core.infrastructure.dao.po.UploadStoreRecordPO selectCurrentVersionRecord(@Param("softwareId") String softwareId);

    @Select("SELECT role_id FROM g_role_software WHERE app_id = #{softwareId}")
    List<String> selectRoleIdsBySoftwareId(@Param("softwareId") String softwareId);

    // ---------- 放行 / 禁止放行 ----------

    @Select("""
            SELECT id, `key`, name, app_name, router_key, type, version, created_at, updated_at,
                   framework, exec_name, location, remote_ip, remote_name, remote_password,
                   active_create, active_expire, order_top, order_time, `desc`, active_code, screenshot, icon
            FROM g_software WHERE id = #{id}
            """)
    SoftwarePO selectById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM g_software WHERE id = #{id}")
    long countById(@Param("id") String id);

    @Insert("INSERT INTO g_white_list (path, created_at) VALUES (#{path}, #{createdAt})")
    int insertWhiteListPath(@Param("path") String path, @Param("createdAt") long createdAt);

    @org.apache.ibatis.annotations.Delete("DELETE FROM g_white_list WHERE path = #{path}")
    int deleteWhiteListPath(@Param("path") String path);

    /** 厂商 `SoftwareMapper.getWebEnter`：`web_entry = 1` 的服务名。 */
    @Select("""
            SELECT sp.service_name FROM g_service_port sp
            LEFT JOIN g_software_app sa ON sa.software_id = sp.software_id
            WHERE sp.software_id = #{softwareId} AND sp.web_entry = 1
            """)
    List<String> selectWebEntryServiceNames(@Param("softwareId") String softwareId);
}
