package com.haojiyou.cnChar;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class CnCharSettingComponent implements Configurable {
    public static final int LENGTH = 45;
    public static final String DEFAULT_STRING = "， , 。 . ： : ； ; ！ ! ？ ? “ \" ” \" ‘ ' ’ ' 【 [ 】 ] （ ( ） ) 「 { 」 } 《 < 》 >".replace(" ", "\n");
    public static final String KEY = "cnchar_config_string";
    private JPanel settingPanel;
    private JTextField[] text1;
    private JTextField[] text2;
    private JLabel[] labels1;
    private JLabel[] labels2;
    private JLabel btnDefault;

    public static void main(String[] args) {
        String[] a = DEFAULT_STRING.split("\n");
        for (int i = 0; i < a.length; i += 2) {
            System.out.println(i + "   " + a[i] + " " + a[i + 1]);
        }
    }

    @Override
    public String getDisplayName() {
        return "ChineseCharReplacce";
    }

    @Override
    @Nullable
    public String getHelpTopic() {
        return "chinese char auto replace";
    }


    @Override
    @Nullable
    public JComponent createComponent() {
        if (settingPanel != null) {
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
            text1[i].setBounds(35 + (i / 15) * 200, 32 * (i % 15), 60, 32);
            text2[i].setBounds(120 + (i / 15) * 200, 32 * (i % 15), 60, 32);
            labels1[i].setBounds(5 + (i / 15) * 200, 32 * (i % 15), 30, 32);
            labels2[i].setBounds(95 + (i / 15) * 200, 32 * (i % 15), 25, 32);
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

        btnDefault = new JLabel();
        btnDefault.setText("恢复默认");
        btnDefault.setForeground(Color.BLUE);
        btnDefault.setBounds(30, 32 * 15, 60, 32);
        btnDefault.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDefault.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int response = JOptionPane.showConfirmDialog(settingPanel,"确定恢复默认吗？",getDisplayName(), JOptionPane.YES_NO_OPTION);
                if (response == 0) {
                    PropertiesComponent.getInstance().setValue(KEY, DEFAULT_STRING);
                    CnCharReplaceTypedHandler.reload();
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
        return !newStr.equals(oldStr);
    }
    @Override
    //用户点击“OK”或“Apply”按钮后会调用该方法，通常用于完成配置信息持久化。
    public void apply() {
        String str = getConfigString();
        PropertiesComponent.getInstance().setValue(KEY, str);
        CnCharReplaceTypedHandler.reload();
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
