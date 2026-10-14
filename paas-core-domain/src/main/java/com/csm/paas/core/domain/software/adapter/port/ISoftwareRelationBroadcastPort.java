package com.csm.paas.core.domain.software.adapter.port;

/**
 * 软件与战位关联变化时推给前端（厂商 `updateSoftwareRelateRole:1546-1548`：
 * `convertAndSend("/topic/software", softwareStatus)`，载荷 `{event:"DELETE", roleList:<roleId>}`）。
 *
 * <p>厂商用的是**一个 static 共享对象**（`RoleService.softwareStatus`），多请求会互相覆盖；
 * 我们每推一次构造一个新载荷（对外可观测的载荷形状不变）。
 */
public interface ISoftwareRelationBroadcastPort {

    /** 推一条"关联被删除"的消息（`event = DELETE`，`roleList` 是那个 roleId）。 */
    void softwareRelationDeleted(String roleId);
}
