package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.ServiceAuthPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IServiceAuthDao {

    @Select("SELECT service_id, auth, auth_type, auth_time, auth_interval FROM g_service_auth")
    List<ServiceAuthPO> selectAll();

    /** 按 service_id 取（厂商 `queryServiceList`：`where service_id = ?` 再取第 0 条）。 */
    @Select("""
            SELECT service_id, auth, auth_type, auth_time, auth_interval
            FROM g_service_auth WHERE service_id = #{serviceId}
            """)
    ServiceAuthPO selectByServiceId(@Param("serviceId") String serviceId);

    /**
     * 把授权状态改成"已过期"（厂商 `softwareRelationServiceList` 里发现 `now > authTime` 时
     * `setAuth(EXPIRED)` + `setUpdatedAt(now)` + `updateByPrimaryKeySelective`——**读接口带副作用**）。
     * <p>表上 `updated_at` 本身有 `ON UPDATE CURRENT_TIMESTAMP`；这里显式写 `NOW()`，与厂商写 Java Date 等价。
     */
    @Update("UPDATE g_service_auth SET auth = #{auth}, updated_at = NOW() WHERE service_id = #{serviceId}")
    int updateAuth(@Param("serviceId") String serviceId, @Param("auth") Integer auth);
}
