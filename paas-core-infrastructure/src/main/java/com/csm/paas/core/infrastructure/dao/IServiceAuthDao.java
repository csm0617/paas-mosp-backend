package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.ServiceAuthPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IServiceAuthDao {

    @Select("SELECT service_id, auth, auth_type, auth_time, auth_interval FROM g_service_auth")
    List<ServiceAuthPO> selectAll();
}
