package com.haojiyou.cnchar.common;

import org.apache.commons.lang3.StringUtils;

/**
 * 描述: 支持的文件类型
 *
 * @author : lixuran
 */
public enum SupportFileType {
    JAVA("java"),
    XML("xml"),
    SQL("sql"),
    HTML("html"),
    JS("js"),
    MARKDOWN("md"),
    PROPERTIES("properties"),
    GIT_IGNORE("gitignore"),
    CSS("css");


    private String type;

    SupportFileType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static SupportFileType getFileType(String fileExtension){
        if (fileExtension == null) {
            return null;
        }

        for (SupportFileType type : SupportFileType.values()){
            if (StringUtils.equalsIgnoreCase(fileExtension, type.type )){
                return type;
            }
        }
        return null;
    }

}
