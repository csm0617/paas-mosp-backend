package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.SoftwarePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /** 软件关联的战位（厂商 `RoleMapper.getRelationRole`：`select id,name,key from g_role where id in (...)`）。 */
    @Select("""
            <script>
            SELECT id, name, `key` FROM g_role
            WHERE id IN <foreach item='item' collection='roleIds' open='(' separator=',' close=')'>#{item}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectRelationRoles(@Param("roleIds") List<String> roleIds);

    @Select("SELECT role_id FROM g_role_software WHERE app_id = #{softwareId}")
    List<String> selectRoleIdsBySoftwareId(@Param("softwareId") String softwareId);

    /** 厂商 `SoftwareMapper.getWebEnter`：`web_entry = 1` 的服务名。 */
    @Select("""
            SELECT sp.service_name FROM g_service_port sp
            LEFT JOIN g_software_app sa ON sa.software_id = sp.software_id
            WHERE sp.software_id = #{softwareId} AND sp.web_entry = 1
            """)
    List<String> selectWebEntryServiceNames(@Param("softwareId") String softwareId);
}
