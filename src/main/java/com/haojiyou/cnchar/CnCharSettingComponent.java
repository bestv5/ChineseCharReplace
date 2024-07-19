package com.haojiyou.cnchar;

import com.haojiyou.cnchar.common.ReplaceCharConfig;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class CnCharSettingComponent implements Configurable {
    public static final int LENGTH = 30;
    public static final int ROW = 10;
    public static final String DEFAULT_STRING = "， , 。 . ： : ； ; ！ ! ？ ? “ \" ” \" ‘ ' ’ ' 【 [ 】 ] （ ( ） ) 「 { 」 } 《 < 》 > 、 /".replace(" ",
            "\n");
    public static final String KEY = "cnchar_config_string";
    public static final String REPLACED_MSG_SHOW_KEY = "cnchar_config_replace_msg_hit";
    public static final String REPLACED_IN_COMMENT_KEY = "cnchar_config_replace_in_comment";

    private JPanel settingPanel;
    private JTextField[] text1;
    private JTextField[] text2;
    private JLabel[] labels1;
    private JLabel[] labels2;
    private JLabel btnDefault;
    private JCheckBox isReplaceMsgHit;
    private JCheckBox replaceInCommentCheckBox;

    private JTextArea extensionWhitelist;

    public static void main(String[] args) {
        String[] a = DEFAULT_STRING.split("\n");
        for (int i = 0; i < a.length; i += 2) {
            System.out.println(i + "   " + a[i] + " " + a[i + 1]);
        }
    }


    @Override
    public String getDisplayName() {
        return "CharAutoReplace";
    }

    @Override
    @Nullable
    public String getHelpTopic() {
        return "char auto replace";
    }


    @Override
    @Nullable
    public JComponent createComponent() {
        if (settingPanel != null ) {
            settingPanel.repaint();
            return settingPanel;
        }

        settingPanel = new JPanel();
        settingPanel.setLayout(null);
        text1 = new JTextField[LENGTH];
        text2 = new JTextField[LENGTH];
        labels1 = new JLabel[LENGTH];
        labels2 = new JLabel[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            text1[i] = new JTextField();
            text2[i] = new JTextField();
            labels1[i] = new JLabel();
            labels2[i] = new JLabel();
            text1[i].setBounds(35 + (i / ROW) * 200, 32 * (i % ROW), 60, 32);
            text2[i].setBounds(120 + (i / ROW) * 200, 32 * (i % ROW), 60, 32);
            labels1[i].setBounds(5 + (i / ROW) * 200, 32 * (i % ROW), 30, 32);
            labels2[i].setBounds(95 + (i / ROW) * 200, 32 * (i % ROW), 25, 32);
            labels1[i].setText((i + 1) + ".");
            labels2[i].setText("->");
            labels1[i].setHorizontalAlignment(JLabel.CENTER);
            labels2[i].setHorizontalAlignment(JLabel.CENTER);
            text1[i].setHorizontalAlignment(JLabel.CENTER);
            text2[i].setHorizontalAlignment(JLabel.CENTER);
            settingPanel.add(text1[i]);
            settingPanel.add(text2[i]);
            settingPanel.add(labels1[i]);
            settingPanel.add(labels2[i]);
        }

        isReplaceMsgHit = new JCheckBox("启用替换提示");
        isReplaceMsgHit.setText("启用替换提示");
        isReplaceMsgHit.setBounds(30, 32 * 15, 200, 32);
        isReplaceMsgHit.setSelected(ReplaceCharConfig.showRepacedMsg);
        settingPanel.add(isReplaceMsgHit);

        replaceInCommentCheckBox = new JCheckBox("启用注释内替换");
        replaceInCommentCheckBox.setText("启用注释内替换");
        replaceInCommentCheckBox.setBounds(30, 34 * 15, 250, 32);
        replaceInCommentCheckBox.setSelected(ReplaceCharConfig.replaceInComment);
        settingPanel.add(replaceInCommentCheckBox);
        //JLabel text = new JLabel();
        //text.setText("不进行替换的文件扩展名配置。格式:.java");
        //text.setForeground(JBColor.BLUE);
        //text.setBounds(30, 36 * 15, 60, 32);
        ////替换白名单
        //extensionWhitelist = new JTextArea();
        //extensionWhitelist.setEditable(true);
        //extensionWhitelist.setText("fsfs");
        //extensionWhitelist.setBounds(30, 25*15, 250,32);
        //settingPanel.add(extensionWhitelist);

        btnDefault = new JLabel();
        btnDefault.setText("恢复默认");
        btnDefault.setForeground(JBColor.BLUE);
        btnDefault.setBounds(30, 36 * 15, 60, 32);
        btnDefault.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDefault.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int response = JOptionPane.showConfirmDialog(settingPanel,"确定恢复默认吗？",getDisplayName(), JOptionPane.YES_NO_OPTION);
                if (response == 0) {
                    PropertiesComponent.getInstance().setValue(KEY, DEFAULT_STRING);
                    PropertiesComponent.getInstance().setValue(REPLACED_MSG_SHOW_KEY, false);
                    ReplaceCharConfig.reload();
                    reset();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {
                btnDefault.setText("<html><u>恢复默认</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnDefault.setText("<html>恢复默认</html>");
            }
        });
        settingPanel.add(btnDefault);

        return settingPanel;
    }

    @Override
    //当用户修改配置参数后，在点击“OK”“Apply”按钮前，框架会自动调用该方法，判断是否有修改，进而控制按钮“OK”“Apply”的是否可用。
    public boolean isModified() {
        String oldStr = PropertiesComponent.getInstance().getValue(KEY, DEFAULT_STRING).trim();
        String newStr = getConfigString().trim();
        String replaceMsgShowOldValue = PropertiesComponent.getInstance().getValue(REPLACED_MSG_SHOW_KEY);
        boolean replaceMsgShowConfigValue = isReplaceMsgHit.isSelected();

        String configedReplaceInCommentOldValue = PropertiesComponent.getInstance().getValue(REPLACED_IN_COMMENT_KEY);
        boolean configedReplaceInCommentValue = replaceInCommentCheckBox.isSelected();

        if (StringUtils.isBlank(replaceMsgShowOldValue) && replaceMsgShowConfigValue){
            return true;
        }else if ((Boolean.parseBoolean(replaceMsgShowOldValue) != replaceMsgShowConfigValue)){
            return true;
        }

        if (StringUtils.isBlank(configedReplaceInCommentOldValue) && configedReplaceInCommentValue){
            return true;
        }else if (Boolean.parseBoolean(configedReplaceInCommentOldValue) != configedReplaceInCommentValue){
            return true;
        }
        return !newStr.equals(oldStr);

    }
    @Override
    //用户点击“OK”或“Apply”按钮后会调用该方法，通常用于完成配置信息持久化。
    public void apply() {
        String str = getConfigString();
        boolean replaceMsgShowConfigValue = isReplaceMsgHit.isSelected();
        boolean configedReplaceInCommentValue = replaceInCommentCheckBox.isSelected();

        PropertiesComponent.getInstance().setValue(KEY, str);
        PropertiesComponent.getInstance().setValue(REPLACED_MSG_SHOW_KEY, replaceMsgShowConfigValue);
        PropertiesComponent.getInstance().setValue(REPLACED_IN_COMMENT_KEY, configedReplaceInCommentValue);
        ReplaceCharConfig.reload();
    }
    @Override
    //点reset按钮,打开页面时调用
    public void reset() {
        String str = PropertiesComponent.getInstance().getValue(KEY, DEFAULT_STRING);
        String[] configString = str.split("\n");
        for (int i = 0; i < LENGTH; i++) {
            text1[i].setText((2 * i) < configString.length ? configString[2 * i].trim() : "");
            text2[i].setText((2 * i + 1) < configString.length ? configString[2 * i + 1].trim() : "");
        }

    }

    private String getConfigString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            sb.append(text1[i].getText().trim()).append("\n").append(text2[i].getText().trim()).append("\n");
        }
        return sb.toString().trim();
    }


}
