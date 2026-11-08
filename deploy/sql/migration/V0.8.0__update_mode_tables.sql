-- 模式标识和模式名值交换
UPDATE `gaop`.`g_mode` SET `mode` = 'FIGHT', `mode_name` = '作战模式' WHERE `id` = '1';
UPDATE `gaop`.`g_mode` SET `mode` = 'TRAIN', `mode_name` = '训练模式' WHERE `id` = '2';
UPDATE `gaop`.`g_mode` SET `mode` = 'MAINTAIN', `mode_name` = '维护模式' WHERE `id` = '3';
