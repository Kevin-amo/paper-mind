package com.lqr.papermind.paperformat.extract;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中文字号名称到磅值的映射器，支持"小五"、"五号"、"四号"等中文字号名称
 */
final class FontSizeNameMapper {

    /** 中文字号名称与对应磅值的映射表 */
    private static final Map<String, Double> VALUES = new LinkedHashMap<>();

    static {
        VALUES.put("小五", 9.0);
        VALUES.put("小5", 9.0);
        VALUES.put("小5号", 9.0);
        VALUES.put("小五号", 9.0);
        VALUES.put("五号", 10.5);
        VALUES.put("5号", 10.5);
        VALUES.put("小四", 12.0);
        VALUES.put("小四号", 12.0);
        VALUES.put("四号", 14.0);
        VALUES.put("三号", 16.0);
        VALUES.put("小三", 15.0);
        VALUES.put("小三号", 15.0);
        VALUES.put("二号", 22.0);
        VALUES.put("小二", 18.0);
        VALUES.put("小二号", 18.0);
    }

    /** 私有构造器，防止实例化 */
    private FontSizeNameMapper() {
    }

    /**
     * 将中文字号名称转换为磅值
     *
     * @param name 字号名称（如"小四"、"五号"）
     * @return 对应的磅值，无法识别时返回null
     */
    static Double toPt(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.replace("字", "").trim();
        return VALUES.get(normalized);
    }
}