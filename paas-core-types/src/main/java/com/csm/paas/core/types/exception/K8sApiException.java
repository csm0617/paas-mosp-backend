package com.csm.paas.core.types.exception;

/**
 * K8s API 调用失败的**领域可见**异常。
 * <p>为什么要这一层：domain 模块**不依赖 fabric8**（分层要求），但业务规则里又需要区分
 * "K8s API 报错"与"其它异常"（厂商对这两类用了不同的 i18n 键）。
 * 于是由 infrastructure 的 port 把 fabric8 异常翻译成本异常，domain 只认它。
 */
public class K8sApiException extends RuntimeException {

    public K8sApiException(String message) {
        super(message);
    }
}
