package com.csm.paas.core.domain.user.service;

/**
 * 修改头像（厂商 `POST /v1/user/update_avatar/{userId}` → `UserService.updateAvatar:1538-1591`）。
 *
 * <p>六步照抄：① 文件 > 200KiB → 400 `global.avatar.size.limit`（控制器层）；
 * ② 用户不存在 → 400 `user.not.found`；③ 算 `icon`：既有头像与本次 `md5` 都非空时比较
 * "旧头像解码后的 md5"与"本次文件 md5" ⇒ 相同 `UN-CHANGE`、否则 `CHANGE`，任一为空 ⇒ `CHANGE`；
 * ④ 组装 `UserStatus{event=MODIFY, userInfo{userId,username,nickname,status,icon}}` 并**推 WebSocket**；
 * ⑤ `g_user.avatar = base64`；⑥ 返回用户详情。
 */
public interface IUserAvatarService {

    /**
     * @param md5 本次上传文件的 md5（用于算 icon 标记，**不落库**）
     * @return 是否推送了 WebSocket 消息（`sendStatus` 为 true 时推送）
     */
    boolean updateAvatar(String userId, String base64, String md5);
}
