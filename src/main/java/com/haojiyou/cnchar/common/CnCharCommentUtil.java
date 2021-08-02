package com.haojiyou.cnchar.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class CnCharCommentUtil {
    private static final Logger LOG = Logger.getInstance(CnCharCommentUtil.class);


    private static final Map<String, String[]> COMMENT_START_MAP = new HashMap<>();
    private static final Map<String, String[]> COMMENT_END_MAP = new HashMap<>();

    static {
        COMMENT_START_MAP.put(SupportFileType.JAVA.getType(), new String[]{"//", "/*"});
        COMMENT_END_MAP.put(SupportFileType.JAVA.getType(), new String[]{"*/"});
        COMMENT_START_MAP.put(SupportFileType.JS.getType(), new String[]{"//", "/*"});
        COMMENT_END_MAP.put(SupportFileType.JS.getType(), new String[]{"*/"});

        COMMENT_START_MAP.put(SupportFileType.SQL.getType(), new String[]{"--", "/*"});
        COMMENT_END_MAP.put(SupportFileType.SQL.getType(), new String[]{"*/"});


        COMMENT_START_MAP.put(SupportFileType.GIT_IGNORE.getType(), new String[]{"#"});
        COMMENT_START_MAP.put(SupportFileType.MARKDOWN.getType(), new String[]{"#", "--"});

        COMMENT_START_MAP.put(SupportFileType.XML.getType(), new String[]{"<!--"});
        COMMENT_END_MAP.put(SupportFileType.XML.getType(), new String[]{"-->"});

    }


    public static boolean isComment(String line, SupportFileType fileType) {
        if (fileType == null) {
            //不支持的文件类型,目前暂不转换
            LOG.info("not supported file type!");
            return true;
        }
        String[] commentStartFlags = COMMENT_START_MAP.get(fileType.getType());
        String[] commentEndFlags = COMMENT_END_MAP.get(fileType.getType());
        boolean isComment = false;
        if (commentStartFlags != null) {
            isComment = StringUtils.endsWithAny(StringUtils.trim(line), commentEndFlags);
            if (!isComment) {
                isComment = StringUtils.startsWithAny(StringUtils.trim(line), commentStartFlags);
            }
        }
        return isComment;
    }

    /**
     * 前提是已经判断出元素是一个注释区域，使用此方法判断是否是注释结尾外
     * @param document
     * @param offset
     * @param fileType
     * @return
     */
    public static boolean isAfterEndOfComment(Document document,Project project,int offset){
       SupportFileType fileType = getSupportFileType(project);
       if (fileType == null){
           return false;
       }
        String lineText = DocumentUtil.getLineText(document, offset);
        lineText = StringUtils.trim(lineText);

        String[] commentEndFlags = COMMENT_END_MAP.get(fileType.getType());
        if (commentEndFlags!=null){
            return StringUtils.containsAny(StringUtils.trim(lineText), commentEndFlags);
        }
        return false;
    }

    /**
     * 是否是自定义注释
     *
     * @param document      文档对象
     * @param project       当前项目对象
     * @param currentOffset 当前光标位置
     * @return
     */
    public static boolean isCustomComment(Document document, Project project, int currentOffset) {
        //当前行文本
        String currentLineText = DocumentUtil.getLineText(document, currentOffset);
        SupportFileType supportFileType = getSupportFileType(project);
        if (supportFileType == null){
            LOG.info("不支持的文件类型,return false");
            return false;
        }
        return isComment(currentLineText, supportFileType);

    }

    public static SupportFileType getSupportFileType(Project project){
        //文件扩展名
        FileEditor selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor();
        if (selectedEditor == null) {
            //此处为空,认为不是当前打开的文件,不能识别的文件扩展名。
            return null;
        }
        VirtualFile selectedEditorFile = selectedEditor.getFile();
        if (selectedEditorFile == null) {
            return null;
        }
        String extension = selectedEditorFile.getExtension();
        return SupportFileType.getFileType(extension);
    }

}
