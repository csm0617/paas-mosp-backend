ALTER TABLE `g_language`
DROP INDEX `uni_lang_version_module_key` ,
ADD UNIQUE INDEX `uni_lang_version_module_key` (`lang`, `lang_version`, `module`(200), `text_key`) USING BTREE COMMENT '同一语言版本的模块下key唯一';

