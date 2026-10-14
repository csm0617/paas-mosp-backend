package com.csm.paas.core.domain.mode.service;

/**
 * 作战模式的写操作（厂商 `ModeController` 的 `POST /v1/mode/updateModeType`）。
 */
public interface IModeCommandService {

    /**
     * 切换作战模式（厂商 `ModeServiceImpl.startModeType:113-143`）。
     *
     * <p>规则：
     * <ol>
     *   <li>列出"业务软件"Deployment（厂商 `getListWithOutGaop("",null,null)`，
     *       `:229-248` 的标签过滤）；</li>
     *   <li>`modeMapper.selectByPrimaryKey(id).get()` —— **不存在直接 NPE ⇒ 500**（照抄）；</li>
     *   <li>对每个服务的第一个容器 env：删掉所有 `name=="MODE"` 且**值不等于新模式**的项
     *       （**边遍历边 remove 的索引 bug 照抄**），有变更才把值改成新模式后追加回列表并 patch；
     *       `env == null` 或没有变更 ⇒ 跳过该服务；</li>
     *   <li>`changeModeTypeDB(id)`：全局 `used=0` 再把该 id 置 `used=1`（返回值被忽略）；</li>
     *   <li>`getCurrentMode()`（`used=1`，没有则 new 空对象）后发通知（本平台只记日志）；</li>
     *   <li>**恒返回 1**。</li>
     * </ol>
     */
    int startModeType(Long id);
}
