package com.dms21.cmdbadmin.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 注册名清洗规则：Eureka 里的名字可能带环境后缀，应用编码 = 去掉环境后缀后的部分。
 *
 * 示例：
 *   order-center-dev1  → order-center / dev1
 *   order-center.stg2  → order-center / stg2
 *   order-center       → order-center / null
 *   service-a          → service-a   / null   （a 不是环境词，不剥离）
 */
public final class ApplicationNames {

    /** 只剥离已知环境词，避免把业务名里的单词误当环境后缀 */
    private static final Pattern ENV_SUFFIX = Pattern.compile(
            "[-_.](dev|test|sit|uat|stg|stage|preprod|pre|prod|prd|gray|sandbox|local)(\\d*)$",
            Pattern.CASE_INSENSITIVE);

    private ApplicationNames() {
    }

    /** 应用编码：去掉环境后缀；没有可识别后缀时原样返回（统一小写） */
    public static String clean(String registryName) {
        String name = normalize(registryName);
        Matcher m = ENV_SUFFIX.matcher(name);
        if (m.find()) {
            String code = name.substring(0, m.start());
            if (!code.isBlank()) {
                return code;
            }
        }
        return name;
    }

    /** 环境标识：命中后缀时返回 dev1 / stg2 等，否则 null */
    public static String env(String registryName) {
        Matcher m = ENV_SUFFIX.matcher(normalize(registryName));
        return m.find() ? (m.group(1) + m.group(2)).toLowerCase(Locale.ROOT) : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
