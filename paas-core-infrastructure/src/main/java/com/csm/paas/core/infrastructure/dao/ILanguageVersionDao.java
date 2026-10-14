package com.csm.paas.core.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ILanguageVersionDao {

    /** 当前语言包版本：`used=1`（厂商 LanguageServiceImpl:383 同款）。 */
    @Select("SELECT lang, lang_version FROM g_language_version WHERE used = 1 LIMIT 1")
    java.util.Map<String, Object> selectUsed();

    /**
     * 一页语言包版本（厂商 `LanguageServiceImpl.getLangVersionList:267-281`：
     * `where lang LIKE %keyword% order by create_time DESC` + PageHelper ⇒ 我们用 offset/limit 等价实现）。
     */
    @Select("""
            <script>
            SELECT id, lang, lang_version, used, create_time FROM g_language_version
            <where>
              <if test="likeKeyword != null">lang LIKE #{likeKeyword}</if>
            </where>
            ORDER BY create_time DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    java.util.List<com.csm.paas.core.infrastructure.dao.po.LanguageVersionPO> selectLanguageVersionPage(
            @org.apache.ibatis.annotations.Param("likeKeyword") String likeKeyword,
            @org.apache.ibatis.annotations.Param("offset") int offset,
            @org.apache.ibatis.annotations.Param("size") int size);

    /** 同一条 where 的总数（PageHelper 的 `total`）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM g_language_version
            <where>
              <if test="likeKeyword != null">lang LIKE #{likeKeyword}</if>
            </where>
            </script>
            """)
    long countLanguageVersions(@org.apache.ibatis.annotations.Param("likeKeyword") String likeKeyword);
}
