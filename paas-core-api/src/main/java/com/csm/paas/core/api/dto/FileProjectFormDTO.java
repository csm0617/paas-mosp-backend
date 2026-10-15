package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增/修改业务软件的请求体（厂商 {@code com.glriverside.gaop.be.controller.form.FileProjectForm}）。
 *
 * <p>厂商出处：
 * {@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller/form/FileProjectForm.java:8-18}。
 *
 * <p>照抄点：只有一个字段 {@code name}，**没有任何校验注解**——空名/重名都交给 service 判重
 * （重名 ⇒ 400 {@code software.name.existed}，带 name 当参数）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileProjectFormDTO {

    private String name;
}
