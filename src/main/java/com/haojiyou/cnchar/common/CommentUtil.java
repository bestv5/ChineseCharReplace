package com.haojiyou.cnchar.common;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
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
        COMMENT_START_MAP.put(SupportFileType.JAVA.getType(), new String[]{"//", "/*", "*"});
        COMMENT_END_MAP.put(SupportFileType.JAVA.getType(), new String[]{"*/"});
        COMMENT_START_MAP.put(SupportFileType.JS.getType(), new String[]{"//", "/*", "*"});
        COMMENT_END_MAP.put(SupportFileType.JS.getType(), new String[]{"*/"});

        COMMENT_START_MAP.put(SupportFileType.SQL.getType(), new String[]{"--", "/*"});
        COMMENT_END_MAP.put(SupportFileType.SQL.getType(), new String[]{"*/"});


        COMMENT_START_MAP.put(SupportFileType.GIT_IGNORE.getType(), new String[] {"#" });
        COMMENT_START_MAP.put(SupportFileType.MARKDOWN.getType(), new String[] {"#","--" });

    }


    public static boolean isComment(String line, SupportFileType fileType) {
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

    /**
     * 是否是自定义注释
     * @param document
     * @param project
     * @param currentOffset  当前光标位置
     * @return
     */
    public static boolean isCustomComment(Document document, Project project, int currentOffset) {
        //当前行文本
        String currentLineText = DocumentUtil.getLineText(document, currentOffset);
        //文件扩展名
        String extension = FileEditorManager.getInstance(project).getSelectedEditor().getFile().getExtension();
        return isComment(currentLineText, SupportFileType.getFileType(extension));

    }




}
