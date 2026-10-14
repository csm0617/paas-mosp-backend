package com.csm.paas.core.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 授权相关的公钥来源（K3 激活码验签）。 */
@Mapper
public interface ILicenceDao {

    /**
     * 厂商 `LicenceService.get():531-545`：`g_activate` 里 `name='gaop'` 那行的公钥。
     * <p>表与列已在真库核对：`g_activate(id,name,public_key,private_key,create_at,update_at)`。
     */
    @Select("SELECT public_key FROM g_activate WHERE name = 'gaop' LIMIT 1")
    String selectGaopActivatePublicKey();

    /**
     * 更新框架公钥（厂商 `updateFrameworkRsaKey:1092-1097` 的
     * `activateMapper.updateByPrimaryKeySelective`：只写 `public_key` 与 `update_at`）。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE g_activate SET public_key = #{publicKey}, update_at = #{updateAt} WHERE name = #{name}
            """)
    int updateActivatePublicKey(@org.apache.ibatis.annotations.Param("name") String name,
                                @org.apache.ibatis.annotations.Param("publicKey") String publicKey,
                                @org.apache.ibatis.annotations.Param("updateAt") long updateAt);

    /**
     * 厂商 `checkSoftwareActiveCode:584-585` + `getPublicKeyAlgorithm:567-579`：
     * `g_auth` 里 `jar_name='default_auth'` 的**最新一条**（`create_at` 降序）公钥。
     * <p><b>表名核对</b>：真库里这张表叫 **`g_auth`**（列 `id,jar_name,store_jar_name,class_name,create_at,
     * update_at,used,public_key,private_key`），不是 `g_auth_info`。我最初凭猜测写成了 `g_auth_info`，
     * 用 `SHOW TABLES`/`SHOW COLUMNS` 核对后才改过来——**表名不能靠猜，必须查真库**。
     */
    @Select("SELECT public_key FROM g_auth WHERE jar_name = 'default_auth' ORDER BY create_at DESC LIMIT 1")
    String selectDefaultAuthPublicKey();
}
