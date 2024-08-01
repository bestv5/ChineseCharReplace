package com.haojiyou.cnchar.common;

import org.apache.commons.lang3.StringUtils;

/**
 * 描述: 支持的文件类型
 *
 * @author : best.xu
 */
public enum SupportFileType {
    //java文件
    JAVA("java"),
    //xml文件
    XML("xml"),
    //sql文件
    SQL("sql"),
    //html文件
    HTML("html"),
    //js文件
    JS("js"),
    //c++文件
    CPP("cpp"),
    //c文件
    H("h"),
    TS("ts"),
    //typescript 文件
    TSX("tsx"),
    //config file
    PROPERTIES("properties"),
    //git 忽略文件
    GIT_IGNORE("gitignore"),
    //css文件
    CSS("css");


    private final String type;

    SupportFileType(String type) {
        this.type = type;
    }

    public static SupportFileType getFileType(String fileExtension) {
        if (fileExtension == null) {
            return null;
        }

        for (SupportFileType type : SupportFileType.values()) {
            if (StringUtils.equalsIgnoreCase(fileExtension, type.type)) {
                return type;
            }
        }
        return null;
    }

    public String getType() {
        return type;
    }

}
