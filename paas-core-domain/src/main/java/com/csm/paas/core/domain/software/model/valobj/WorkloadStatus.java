package com.csm.paas.core.domain.software.model.valobj;

/**
 * 由 {@link WorkloadStateVO} 推导服务状态——厂商 `getServiceStatusByServicesItem` 的**两个重载**
 * （`ServiceQueryServiceImpl` 里原先的私有方法，本轮抽出来共用）。
 *
 * <p>返回串照抄：`"STOP"` / `"RUNNING"` / `" ROLLING_UPDATE"`（**前导空格**）/ `"STARING"`（厂商拼写）。
 * <p>Deployment 与 StatefulSet **判断条件不同**（Deployment 多要求 `unavailableReplicas != null`）。
 */
public final class WorkloadStatus {

    private WorkloadStatus() {
    }

    public static String of(WorkloadStateVO s) {
        if (s == null) {
            return "STOP";
        }
        if (s.isDeployment()) {
            if (s.getAvailableReplicas() != null) {
                if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                    if (s.getUnavailableReplicas() != null
                            && s.getReadyReplicas() > s.getAvailableReplicas()) {
                        return " ROLLING_UPDATE";
                    }
                    return "RUNNING";
                }
            } else {
                if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null
                        && s.getUnavailableReplicas() != null) {
                    return "RUNNING";
                }
                if (s.getReplicas() != null && s.getUpdatedReplicas() != null
                        && s.getUnavailableReplicas() != null) {
                    return "STARING";
                }
            }
            return "STOP";
        }
        if (s.getAvailableReplicas() != null) {
            if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                if (s.getReadyReplicas() > s.getAvailableReplicas()) {
                    return " ROLLING_UPDATE";
                }
                return "RUNNING";
            }
        } else {
            if (s.getReadyReplicas() != null && s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                return "RUNNING";
            }
            if (s.getReplicas() != null && s.getUpdatedReplicas() != null) {
                return "STARING";
            }
        }
        return "STOP";
    }
}
