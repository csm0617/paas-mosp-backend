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

    /**
     * 某语言+某版本的版本行（厂商 `deleteLangVersion:354` 的
     * `where lang = ? and lang_version = ?`，取第 0 条）。
     */
    @Select("""
            SELECT id, lang, lang_version, used, create_time FROM g_language_version
            WHERE lang = #{lang} AND lang_version = #{version}
            LIMIT 1
            """)
    com.csm.paas.core.infrastructure.dao.po.LanguageVersionPO selectByLangAndVersion(
            @org.apache.ibatis.annotations.Param("lang") String lang,
            @org.apache.ibatis.annotations.Param("version") String version);

    /** 按主键删版本行（厂商 `deleteByPrimaryKey`）。 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM g_language_version WHERE id = #{id}")
    int deleteVersionById(@org.apache.ibatis.annotations.Param("id") Long id);

    /** 删某语言+某版本下的**全部词条**（厂商 `languageMapper.delete(where lang = ? and lang_version = ?)`）。 */
    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM g_language WHERE lang = #{lang} AND lang_version = #{version}
            """)
    int deleteLanguageEntries(@org.apache.ibatis.annotations.Param("lang") String lang,
                              @org.apache.ibatis.annotations.Param("version") String version);

    /**
     * 某语言+版本下的词条（厂商 `getLanguages:485-490`：`where lang=? and lang_version=?`
     * **加上可选的 module**——module 为空白时不过滤）。
     */
    @Select("""
            <script>
            SELECT id, text_key, content, lang, lang_version, module, abbr, description
            FROM g_language
            WHERE lang = #{lang} AND lang_version = #{version}
            <if test="module != null and module != ''">AND module = #{module}</if>
            </script>
            """)
    java.util.List<com.csm.paas.core.infrastructure.dao.po.LanguageEntryPO> selectLanguages(
            @org.apache.ibatis.annotations.Param("lang") String lang,
            @org.apache.ibatis.annotations.Param("version") String version,
            @org.apache.ibatis.annotations.Param("module") String module);

    /** 把所有 `used=1` 清成 0（厂商 `updateLanguageVersion:465` 的第一步，**全局**）。 */
    @org.apache.ibatis.annotations.Update("UPDATE g_language_version SET used = 0 WHERE used = 1")
    int resetUsed();

    /** 把某语言+版本置为 `used=1`，返回受影响行数（厂商同方法的第二步）。 */
    @org.apache.ibatis.annotations.Update("""
            UPDATE g_language_version SET used = 1 WHERE lang = #{lang} AND lang_version = #{version}
            """)
    int setUsed(@org.apache.ibatis.annotations.Param("lang") String lang,
                @org.apache.ibatis.annotations.Param("version") String version);

    /** 某语言+版本的版本行数（厂商 `setLanguageVersion:431` 的 `count`）。 */
    @Select("""
            SELECT COUNT(*) FROM g_language_version WHERE lang = #{lang} AND lang_version = #{version}
            """)
    long countByLangAndVersion(@org.apache.ibatis.annotations.Param("lang") String lang,
                               @org.apache.ibatis.annotations.Param("version") String version);
}
