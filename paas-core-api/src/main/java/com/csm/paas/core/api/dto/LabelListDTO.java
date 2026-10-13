package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `LabelList`：`{name, labels}` —— `name` 是 **label 的键**，`labels` 是该键在集群里出现过的**去重值**。
 * <p>注意两个名字都容易读反：`name` 不是节点名，`labels` 不是"标签串"。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LabelListDTO {

    private String name;
    private List<String> labels;
}
