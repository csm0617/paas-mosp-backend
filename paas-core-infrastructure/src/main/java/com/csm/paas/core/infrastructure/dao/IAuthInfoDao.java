package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.AuthInfoPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * `g_auth` 的读/写（厂商 `AuthInfoMapper`；表名见 `AuthInfoDynamicSqlSupport`：`g_auth`）。
 */
@Mapper
public interface IAuthInfoDao {

    /**
     * 厂商 `DynamicLoadingService.updateDefaultAuth:86-88`：
     * `where jar_name = ? order by create_at DESC`，取第 0 条。
     */
    @Select("""
            SELECT id, jar_name, store_jar_name, class_name, create_at, update_at, used, public_key, private_key
            FROM g_auth
            WHERE jar_name = #{jarName}
            ORDER BY create_at DESC
            LIMIT 1
            """)
    AuthInfoPO selectLatestByJarName(@Param("jarName") String jarName);

    /**
     * 厂商同方法 `:90-94` 的 `updateByPrimaryKeySelective`（只 set 这四列，
     * 其余列保持原值——所以不能写成整行 UPDATE）。
     */
    @Update("""
            UPDATE g_auth
            SET used = #{used}, public_key = #{publicKey}, private_key = #{privateKey}, update_at = #{updateAt}
            WHERE id = #{id}
            """)
    int updateDefaultAuthKeys(@Param("id") Integer id,
                              @Param("used") Integer used,
                              @Param("publicKey") String publicKey,
                              @Param("privateKey") String privateKey,
                              @Param("updateAt") Long updateAt);
}
