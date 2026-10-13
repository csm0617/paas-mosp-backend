package com.csm.paas.core.trigger;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;

/**
 * 契约对比用的"线上等价"判定：JSON 里的数字**按数值**比较，不按节点类型。
 * <p>实例：厂商返回 `"total": 2`（IntNode），重写版返回 `long` → LongNode；线上字节都是 `2`，
 * 若按 `JsonNode.equals` 就会误报差异。这正是工单 08 要讨论的"逐字节等价 vs 语义等价"的微观形态。
 */
public final class JsonEquivalence {

    private JsonEquivalence() {
    }

    public static boolean equivalent(JsonNode a, JsonNode b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        if (a.isObject() && b.isObject()) {
            if (!fieldNames(a).equals(fieldNames(b))) {
                return false;
            }
            for (Iterator<String> it = a.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!equivalent(a.get(name), b.get(name))) {
                    return false;
                }
            }
            return true;
        }
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!equivalent(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    private static java.util.Set<String> fieldNames(JsonNode node) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }
}
