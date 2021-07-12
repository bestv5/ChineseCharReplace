package com.haojiyou.cnChar.common;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class CommentUtil {

    private static HashMap COMMENT_START_MAP = new HashMap();
    private static HashMap COMMENT_END_MAP = new HashMap();

    static {
        COMMENT_START_MAP.put(FileType.JAVA.getType(), new String[]{"//", "/*", "*"});
        COMMENT_END_MAP.put(FileType.JAVA.getType(), new String[]{"*/"});
        COMMENT_START_MAP.put(FileType.JS.getType(), new String[]{"//", "/*", "*"});
        COMMENT_END_MAP.put(FileType.JS.getType(), new String[]{"*/"});

        COMMENT_START_MAP.put(FileType.SQL.getType(), new String[]{"--", "/*"});
        COMMENT_END_MAP.put(FileType.SQL.getType(), new String[]{"*/"});


    }


    public static boolean isComment(String line, FileType fileType) {
        if (fileType == null) {
            //不支持的文件类型,目前暂不转换
            return true;
        }
        String[] commentStartFlags = (String[]) COMMENT_START_MAP.get(fileType.getType());
        String[] commentEndFlags = (String[]) COMMENT_END_MAP.get(fileType.getType());
        boolean result = false;
        if (commentStartFlags != null){
            result = StringUtils.startsWithAny(StringUtils.trim(line),commentStartFlags );
        }
        if (!result){
            result = StringUtils.endsWithAny(StringUtils.trim(line),commentEndFlags );
        }

        return result;
    }

}
