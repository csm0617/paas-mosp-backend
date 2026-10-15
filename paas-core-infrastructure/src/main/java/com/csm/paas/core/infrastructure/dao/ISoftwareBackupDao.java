package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.ServicePortPO;
import com.csm.paas.core.infrastructure.dao.po.SoftwarePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 软件整包备份/还原的库访问（厂商 `exportSoftwareForms` / `importSoftwareInfo` 用到的那些查询与写入）。
 *
 * <p>刻意**不**改动既有 `ISoftwareDao`：这里只放备份/还原新增的 SQL（按 id 批量取软件、按 key 取、
 * 战位 key 反查、端口行全量、`g_software_app` 的插入/删除、以及 `importSoftwareInfo` 的整行 update）。
 */
@Mapper
public interface ISoftwareBackupDao {

    /** 厂商 `softwareMapper.select(where id in (...))`（`exportSoftwareForms` 的 type=2 分支）。 */
    @Select("""
            <script>
            SELECT id, `key`, name, app_name, router_key, type, version, created_at, updated_at,
                   framework, exec_name, location, remote_ip, remote_name, remote_password,
                   active_create, active_expire, order_top, order_time, `desc`, active_code, screenshot, icon
            FROM g_software WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<SoftwarePO> selectByIds(@Param("ids") List<String> ids);

    /** 厂商 `softwareMapper.select(SelectDSLCompleter.allRows())`（type=1 分支）。 */
    @Select("""
            SELECT id, `key`, name, app_name, router_key, type, version, created_at, updated_at,
                   framework, exec_name, location, remote_ip, remote_name, remote_password,
                   active_create, active_expire, order_top, order_time, `desc`, active_code, screenshot, icon
            FROM g_software
            """)
    List<SoftwarePO> selectAll();

    /** 厂商 `softwareMapper.selectOne(where key = ?)`。 */
    @Select("""
            SELECT id, `key`, name, app_name, router_key, type, version, created_at, updated_at,
                   framework, exec_name, location, remote_ip, remote_name, remote_password,
                   active_create, active_expire, order_top, order_time, `desc`, active_code, screenshot, icon
            FROM g_software WHERE `key` = #{key} LIMIT 1
            """)
    SoftwarePO selectByKey(@Param("key") String key);

    /**
     * 厂商 `roleMapper.select(left join role_software on role.id = role_software.role_id
     * where role_software.app_id = ?)` 后取 `role.key`。
     */
    @Select("""
            SELECT r.`key` FROM g_role r
            INNER JOIN g_role_software rs ON rs.role_id = r.id
            WHERE rs.app_id = #{softwareId}
            """)
    List<String> selectRoleKeysBySoftwareId(@Param("softwareId") String softwareId);

    /** 厂商 `getSoftwareServicePort(softwareId)`：该软件全部端口行（无 orderBy）。 */
    @Select("""
            SELECT id, software_id, service_name, port, path_name, web_entry, allow, updated_at
            FROM g_service_port WHERE software_id = #{softwareId}
            """)
    List<ServicePortPO> selectServicePortsBySoftware(@Param("softwareId") String softwareId);

    /** 厂商 `servicePortMapper.select(where software_id = ? and web_entry = true)` 后取第 0 条。 */
    @Select("""
            SELECT id, software_id, service_name, port, path_name, web_entry, allow, updated_at
            FROM g_service_port WHERE software_id = #{softwareId} AND web_entry = true LIMIT 1
            """)
    ServicePortPO selectFirstWebEntry(@Param("softwareId") String softwareId);

    /** 按 软件+服务+端口 取（还原时判断该行已存在则改 path_name/updated_at）。 */
    @Select("""
            SELECT id, software_id, service_name, port, path_name, web_entry, allow, updated_at
            FROM g_service_port WHERE software_id = #{softwareId} AND service_name = #{serviceName}
              AND port = #{port} LIMIT 1
            """)
    ServicePortPO selectServicePort(@Param("softwareId") String softwareId,
                                    @Param("serviceName") String serviceName, @Param("port") int port);

    /** 插一行 `g_service_port`（还原时用；`allow` 留 NULL，与厂商 `insertSelective` 一致）。 */
    @Insert("""
            INSERT INTO g_service_port (software_id, service_name, port, path_name, web_entry, updated_at)
            VALUES (#{softwareId}, #{serviceName}, #{port}, #{pathName}, #{webEntry}, #{updatedAt})
            """)
    int insertServicePort(@Param("softwareId") String softwareId, @Param("serviceName") String serviceName,
                          @Param("port") int port, @Param("pathName") String pathName,
                          @Param("webEntry") boolean webEntry, @Param("updatedAt") long updatedAt);

    /** 只更新该行的 `path_name`/`updated_at`（厂商 `updateByPrimaryKeySelective`）。 */
    @Update("UPDATE g_service_port SET path_name = #{pathName}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateServicePortPathAndTime(@Param("id") long id, @Param("pathName") String pathName,
                                     @Param("updatedAt") long updatedAt);

    /** 厂商 `softwareAppMapper.insert(insertSelective)`（`updated_at` 留 NULL）。 */
    @Insert("""
            INSERT INTO g_software_app (id, software_id, app_name, created_at, updated_at)
            VALUES (#{id}, #{softwareId}, #{appName}, #{createdAt}, NULL)
            """)
    int insertSoftwareApp(@Param("id") String id, @Param("softwareId") String softwareId,
                          @Param("appName") String appName, @Param("createdAt") long createdAt);

    /** 厂商 `softwareAppMapper.delete(where software_id = ? and app_name in (...))`。 */
    @Delete("""
            <script>
            DELETE FROM g_software_app WHERE software_id = #{softwareId} AND app_name IN
            <foreach collection="names" item="n" open="(" separator="," close=")">#{n}</foreach>
            </script>
            """)
    int deleteSoftwareAppsByNames(@Param("softwareId") String softwareId, @Param("names") List<String> names);

    /**
     * 厂商 `importSoftwareInfo:1196-1250` 的整行 update：`BeanUtils.copyProperties(softwareForm, sf)` 之后
     * `setName`/`setKey`/`setCreatedAt(now)`/`setOrderTop(0)`/`setDesc`/`setScreenshot`，再 `updateByPrimaryKey`
     * ——即**所有列都写**（含 `created_at`；这是既有 `ISoftwareDao.updateByPrimaryKey` 不做的一列）。
     */
    @Update("""
            UPDATE g_software SET `key` = #{key}, name = #{name}, app_name = #{appName}, router_key = #{routerKey},
                                  type = #{type}, version = #{version}, created_at = #{createdAt},
                                  updated_at = #{updatedAt}, framework = #{framework}, exec_name = #{execName},
                                  location = #{location}, remote_ip = #{remoteIp}, remote_name = #{remoteName},
                                  remote_password = #{remotePassword}, active_create = #{activeCreate},
                                  active_expire = #{activeExpire}, order_top = #{orderTop}, order_time = #{orderTime},
                                  `desc` = #{desc}, active_code = #{activeCode}, screenshot = #{screenshot},
                                  icon = #{icon}
            WHERE id = #{id}
            """)
    int updateByPrimaryKeyForImport(SoftwarePO software);
}
