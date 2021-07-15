package com.haojiyou.cnchar.common;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class CommentUtil {

    private static final Map<String, String[]> COMMENT_START_MAP = new HashMap<>();
    private static final Map<String, String[]> COMMENT_END_MAP = new HashMap<>();

    static {
        COMMENT_START_MAP.put(FileType.JAVA.getType(), new String[]{"//", "/*", "*"});
        COMMENT_END_MAP.put(FileType.JAVA.getType(), new String[]{"*/"});
        COMMENT_START_MAP.put(FileType.JS.getType(), new String[]{"//", "/*", "*"});
        COMMENT_END_MAP.put(FileType.JS.getType(), new String[]{"*/"});

        COMMENT_START_MAP.put(FileType.SQL.getType(), new String[]{"--", "/*"});
        COMMENT_END_MAP.put(FileType.SQL.getType(), new String[]{"*/"});


        COMMENT_START_MAP.put(FileType.GIT_IGNORE.getType(), new String[] {"#" });
        COMMENT_START_MAP.put(FileType.MARKDOWN.getType(), new String[] {"#","--" });

    }


    public static boolean isComment(String line, FileType fileType) {
        if (fileType == null) {
            //不支持的文件类型,目前暂不转换
            return true;
        }
        String[] commentStartFlags = COMMENT_START_MAP.get(fileType.getType());
        String[] commentEndFlags = COMMENT_END_MAP.get(fileType.getType());
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
