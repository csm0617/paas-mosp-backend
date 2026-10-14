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

    /**
     * 授权列表分页（厂商 `getSoftwareAuthList:3290`）：name 模糊 / type 等值，
     * **只按 `created_at DESC` 排序**——注意与列表端点的"`order_top` 恒在最前"不同。
     */
    @Select("""
            <script>
            SELECT id, `key`, name, app_name, router_key, type, version, created_at, updated_at,
                   framework, exec_name, location, remote_ip, remote_name, remote_password,
                   active_create, active_expire, order_top, order_time, `desc`, active_code, screenshot, icon
            FROM g_software
            <where>
              <!-- 厂商 getSoftwareAuthList 的 likeName **只打在 name 上**（不像列表端点那样 name OR key） -->
              <if test="likeName != null">name LIKE #{likeName}</if>
              <if test="type != null">AND type = #{type}</if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<SoftwarePO> selectAuthPage(@Param("likeName") String likeName, @Param("type") String type,
                                    @Param("offset") int offset, @Param("size") int size);

    /** 授权列表总数——**必须与 `selectAuthPage` 用同一套 WHERE**（name 只打在 name 上），否则 total 会与 list 不一致。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM g_software
            <where>
              <if test="likeName != null">name LIKE #{likeName}</if>
              <if test="type != null">AND type = #{type}</if>
            </where>
            </script>
            """)
    long selectAuthCount(@Param("likeName") String likeName, @Param("type") String type);

    /**
     * 软件在某服务下的端口映射（厂商 `SoftwareService.servicePortList:2304-2306`）：
     * `where software_id = ? and service_name = ?`——**不排序**（厂商没写 orderBy，照抄）。
     */
    @Select("""
            SELECT id, software_id, service_name, port, path_name, web_entry, allow, updated_at
            FROM g_service_port
            WHERE software_id = #{softwareId} AND service_name = #{serviceName}
            """)
    List<com.csm.paas.core.infrastructure.dao.po.ServicePortPO> selectServicePorts(
            @Param("softwareId") String softwareId, @Param("serviceName") String serviceName);

    /**
     * 某软件下所有服务的路径名（厂商 `SoftwareMapper.allServicePathBySoftware` 的三表 join 照抄）：
     * `g_software_app sp inner join g_software s on sp.software_id=s.id
     *  inner join g_service_port st on sp.app_name=st.service_name where sp.software_id=?`
     */
    @Select("""
            SELECT st.path_name FROM g_software_app sp
            INNER JOIN g_software s ON sp.software_id = s.id
            INNER JOIN g_service_port st ON sp.app_name = st.service_name
            WHERE sp.software_id = #{softwareId}
            """)
    List<String> selectServicePathsBySoftware(@Param("softwareId") String softwareId);

    /** 同一软件+服务下该路径是否已被占用（厂商 `isServicePathExist:2325-2326`）。 */
    @Select("""
            SELECT COUNT(*) FROM g_service_port
            WHERE software_id = #{softwareId} AND service_name = #{serviceName} AND path_name = #{pathName}
            """)
    long countServicePath(@Param("softwareId") String softwareId, @Param("serviceName") String serviceName,
                          @Param("pathName") String pathName);

    /** 按 软件+服务+端口 取（厂商 updateSoftwareServicePort 的定位条件）。 */
    @Select("""
            SELECT id, software_id, service_name, port, path_name, web_entry, allow, updated_at
            FROM g_service_port
            WHERE software_id = #{softwareId} AND service_name = #{serviceName} AND port = #{port}
            LIMIT 1
            """)
    com.csm.paas.core.infrastructure.dao.po.ServicePortPO selectServicePort(
            @Param("softwareId") String softwareId, @Param("serviceName") String serviceName,
            @Param("port") int port);

    /** 选择性更新 path_name（厂商 `updateByPrimaryKeySelective`，只设了 path_name）。 */
    @org.apache.ibatis.annotations.Update("UPDATE g_service_port SET path_name = #{pathName} WHERE id = #{id}")
    int updateServicePortPath(@Param("id") long id, @Param("pathName") String pathName);

    @Select("""
            SELECT id, software_id, service_name, port, path_name, web_entry, allow, updated_at
            FROM g_service_port WHERE id = #{id}
            """)
    com.csm.paas.core.infrastructure.dao.po.ServicePortPO selectServicePortById(@Param("id") long id);

    /**
     * 清空某软件的授权字段（厂商 `cancelAuth:3199-3205`：`active_code`/`active_create`/`active_expire` 置 null，
     * `updated_at` 设为传入时间）。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE g_software SET active_code = NULL, active_create = NULL, active_expire = NULL,
                                  updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int clearSoftwareAuth(@Param("id") String id, @Param("updatedAt") long updatedAt);

    /**
     * 某服务的 Pod 重启记录（厂商 `GET /v1/service/restartInfo` → `podEventMapper.select(...)`）：
     * `where service_name = ? order by create_at desc`——**不映射其它列**，只取三个字段。
     */
    @Select("""
            SELECT pod_name, restart_count, create_at FROM g_pod_event
            WHERE service_name = #{serviceName}
            ORDER BY create_at DESC
            """)
    List<com.csm.paas.core.infrastructure.dao.po.PodEventPO> selectPodEvents(@Param("serviceName") String serviceName);

    /**
     * 写入软件授权（厂商 `authSoftware:3264-3267`：设 `active_create`/`active_code`/`active_expire` 后整行更新；
     * **不设 `updated_at`**——照抄）。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE g_software SET active_create = #{activeCreate}, active_code = #{activeCode},
                                  active_expire = #{activeExpire}
            WHERE id = #{id}
            """)
    int saveSoftwareAuth(@Param("id") String id, @Param("activeCreate") long activeCreate,
                         @Param("activeCode") String activeCode, @Param("activeExpire") long activeExpire);

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

    // ---------- patchService 的 B 分支（厂商 ServiceController.updateServiceDiscovery:1107-1136）----------

    /** `g_software_app` 里 `app_name` 命中的软件 id（厂商 `where app_name = ?` 再 map `getSoftwareId`）。 */
    @Select("SELECT software_id FROM g_software_app WHERE app_name = #{appName}")
    List<String> selectSoftwareIdsByAppName(@Param("appName") String appName);

    /**
     * 某软件关联的全部 `app_name`（厂商 `softwareAllService:2219-2221` / `softwareRelationService:2223-2226`：
     * `where software_id = ?`，**没有 orderBy** ⇒ 库返回顺序）。
     */
    @Select("SELECT app_name FROM g_software_app WHERE software_id = #{softwareId}")
    List<String> selectAppNamesBySoftwareId(@Param("softwareId") String softwareId);

    /** 删某软件在某服务下的**全部**端口映射（厂商 `where software_id = ? and service_name = ?`）。 */
    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM g_service_port WHERE software_id = #{softwareId} AND service_name = #{serviceName}
            """)
    int deleteServicePortsBySoftwareIdAndServiceName(@Param("softwareId") String softwareId,
                                                     @Param("serviceName") String serviceName);

    /**
     * 批量插端口映射（厂商 `ServicePortMapper.insertBatch`，`:139-140` 的 `<foreach>` 单条 insert）。
     * <p>**空列表由调用方拦下**：厂商那个 `<foreach>` 对空列表会拼出 `INSERT … VALUES`（非法 SQL）⇒ 400；
     * 我们在领域层显式抛同一个键（`findings/118` §3 的第 2 条怪点），所以这里对空列表直接返回 0。
     */
    @org.apache.ibatis.annotations.Insert("""
            <script>
            INSERT INTO g_service_port (software_id, service_name, port, path_name, web_entry, allow, updated_at)
            VALUES
            <foreach collection='list' item='item' separator=','>
              (#{item.softwareId}, #{item.serviceName}, #{item.port}, #{item.pathName},
               #{item.webEntry}, #{item.allow}, #{item.updatedAt})
            </foreach>
            </script>
            """)
    int insertServicePorts(@Param("list") List<com.csm.paas.core.infrastructure.dao.po.ServicePortPO> rows);

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
