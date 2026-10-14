package com.csm.paas.core.domain.whitelist.service;

import com.csm.paas.core.domain.whitelist.model.entity.WhiteListEntity;
import com.csm.paas.core.domain.whitelist.model.entity.WhiteListInfoEntity;

import java.util.List;

/**
 * 白名单（厂商 `WhiteListController` + `WhiteListService:36-80`）。
 *
 * <p>三个端点：`GET /v1/white_list/list`、`POST /v1/white_list?path=`、`DELETE /v1/white_list?pathId=a,b`。
 *
 * <p><b>照抄点</b>：
 * <ul>
 *   <li>新增时查重用的是**原样的 path**（不是 `trim()` 后的），但**入库写的是 `trim()` 后的**；
 *       插入后再按**原样 path** 回查返回 ⇒ 带首尾空格的 path 会返回 **null**；</li>
 *   <li>删路径时逐个 id：**查不到就 400**（不是静默跳过），带 id 作为参数；</li>
 *   <li>新增/删除之后都会**异步**通知网关 `GET http://<host>:<port>/refresh`（fire-and-forget，失败不影响响应）。</li>
 * </ul>
 */
public interface IWhiteListService {

    WhiteListInfoEntity list(int page, int size, String keyword);

    /** @throws com.csm.paas.core.types.exception.WrongArgumentException 该 path 已存在 ⇒ `software.path.already.exists` */
    WhiteListEntity addPath(String path);

    /** @throws com.csm.paas.core.types.exception.WrongArgumentException 某个 id 不存在 ⇒ `white.list.delete.path.not.exist` */
    void deletePaths(List<Long> pathIds);
}
