package com.csm.paas.core.domain.licence;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机器码集合的两条规则（厂商 `LicenceService.stringToHashSetStream` / `hasIntersectionStream`，`:551-565`）。
 * <p><b>分隔符是分号 `;`，不是逗号</b>——逗号是"机器码内部各段"的分隔符（见 `MachineCodeVO.parse`），
 * 分号才是"同一个字段里多个机器码"的分隔符。这两层很容易看反。
 * <p>交集判定对 null 一律返回 false（厂商同款）。
 */
public final class LicenceSets {

    private LicenceSets() {
    }

    public static Set<String> parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(input.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static boolean hasIntersection(Set<String> a, Set<String> b) {
        if (a == null || b == null) {
            return false;
        }
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = a.size() > b.size() ? a : b;
        return smaller.stream().anyMatch(larger::contains);
    }
}
