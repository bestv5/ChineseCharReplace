package com.haojiyou.cnchar.settings;

import com.haojiyou.cnchar.region.InputRegion;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述: 新设置 UI —— FormBuilder 布局 + JBTable 映射表 + 每区三态控件。
 *
 * <p>替代旧 {@code CnCharSettingComponent}（绝对布局 + 固定 30 行文本框）。
 * 帮助主题 {@code CharAutoReplace.settings}，由 CharAutoReplaceHelpProvider 解析为在线文档。
 *
 * @author : best.xu
 */
public class CharAutoReplaceConfigurable implements Configurable {

    /** 区域三态下拉的显示文案（索引与 ReplaceMode.ordinal() 对应）。 */
    private static final String[] MODE_LABELS = {"开 (ALWAYS)", "关 (NEVER)", "自适应 (ADAPTIVE)"};

    private JPanel mainPanel;
    private MappingTableModel mappingTableModel;
    private JBTable mappingTable;
    private JCheckBox showHintCheckBox;
    private ComboBox<RegionPolicy.ReplaceMode> codeModeCombo;
    private ComboBox<RegionPolicy.ReplaceMode> commentModeCombo;
    private ComboBox<RegionPolicy.ReplaceMode> stringModeCombo;
    private ComboBox<RegionPolicy.ReplaceMode> commitModeCombo;
    private ComboBox<RegionPolicy.ReplaceMode> consoleModeCombo;
    private ComboBox<RegionPolicy.ReplaceMode> plainTextModeCombo;

    @Override
    public String getDisplayName() {
        return "CharAutoReplace";
    }

    /**
     * 帮助主题：配合 plugin.xml 注册的
     * {@code <webHelpProvider implementation="com.haojiyou.cnchar.service.CharAutoReplaceHelpProvider"/>}
     * 解析为项目在线文档地址（设置页 F1 帮助）。
     */
    @Override
    public String getHelpTopic() {
        return "CharAutoReplace.settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        RegionPolicy.ReplaceMode[] modes = RegionPolicy.ReplaceMode.values();
        codeModeCombo = createModeCombo(modes);
        commentModeCombo = createModeCombo(modes);
        stringModeCombo = createModeCombo(modes);
        commitModeCombo = createModeCombo(modes);
        consoleModeCombo = createModeCombo(modes);
        plainTextModeCombo = createModeCombo(modes);
        showHintCheckBox = new JCheckBox("显示替换提示");

        // FormBuilder 链式布局：保持原 UI 的组件、顺序与文案
        // （映射表 → 分隔线 → 六个区域下拉 → 自适应提示 → 分隔线 → 显示替换提示 → 恢复默认）。
        FormBuilder builder = FormBuilder.createFormBuilder()
                .addComponent(createMappingPanel())
                .addSeparator()
                .addLabeledComponent("代码区 (CODE):", codeModeCombo)
                .addLabeledComponent("控制台 (CONSOLE):", consoleModeCombo)
                .addLabeledComponent("注释区 (COMMENT):", commentModeCombo)
                .addLabeledComponent("纯文本 (PLAIN_TEXT):", plainTextModeCombo)
                .addLabeledComponent("字符串区 (STRING):", stringModeCombo)
                .addLabeledComponent("提交框 (COMMIT):", commitModeCombo)
                .addComponent(createAdaptiveHintLabel())
                .addSeparator()
                .addComponent(showHintCheckBox)
                .addComponent(createResetLink())
                .addComponentFillVertically(new JPanel(), 0);

        mainPanel = builder.getPanel();
        return mainPanel;
    }

    private JComponent createMappingPanel() {
        mappingTableModel = new MappingTableModel();
        mappingTable = new JBTable(mappingTableModel);
        mappingTable.setRowHeight(28);
        mappingTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        mappingTable.getColumnModel().getColumn(1).setPreferredWidth(120);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(mappingTable)
                .setAddAction(button -> addMappingRow())
                .setRemoveAction(button -> removeSelectedMappingRow());

        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("自定义映射（用户自定义映射优先级最高）");
        panel.add(title, BorderLayout.NORTH);
        panel.setPreferredSize(new Dimension(400, 200));
        panel.add(decorator.createPanel(), BorderLayout.CENTER);
        return panel;
    }

    private void addMappingRow() {
        mappingTableModel.addRow(new MappingRule("", ""));
        int lastRow = mappingTableModel.getRowCount() - 1;
        mappingTable.setRowSelectionInterval(lastRow, lastRow);
        mappingTable.editCellAt(lastRow, 0);
    }

    private void removeSelectedMappingRow() {
        int selectedRow = mappingTable.getSelectedRow();
        if (selectedRow >= 0) {
            mappingTableModel.removeRow(selectedRow);
        }
    }

    private ComboBox<RegionPolicy.ReplaceMode> createModeCombo(RegionPolicy.ReplaceMode[] modes) {
        ComboBox<RegionPolicy.ReplaceMode> combo = new ComboBox<>(modes);
        combo.setRenderer(new ModeListCellRenderer(MODE_LABELS));
        return combo;
    }

    private JComponent createAdaptiveHintLabel() {
        JLabel hint = new JLabel("<html><i>自适应为启发式判断：按光标前文中英文语境决定</i></html>");
        hint.setForeground(Color.GRAY);
        return hint;
    }

    private JComponent createResetLink() {
        // 平台标准链接控件 ActionLink（extends JButton，com.intellij.ui.components 包），
        // 文案与触发行为保持不变。
        // 显式指定 ActionListener 目标类型：ActionLink 另有 (String, Function1) 构造器，
        // 不加 cast 时 void lambda 会触发 "reference to ActionLink is ambiguous" 编译错误。
        return new ActionLink("恢复默认", (ActionListener) e -> resetToDefaults());
    }

    private void resetToDefaults() {
        int response = JOptionPane.showConfirmDialog(
                mainPanel, "确定恢复默认设置吗？所有自定义映射和区域策略将被重置。",
                getDisplayName(), JOptionPane.YES_NO_OPTION);
        if (response != JOptionPane.YES_OPTION) {
            return;
        }
        mappingTableModel.setRules(new ArrayList<>());
        showHintCheckBox.setSelected(false);
        codeModeCombo.setSelectedItem(RegionPolicy.defaultMode(InputRegion.CODE));
        commentModeCombo.setSelectedItem(RegionPolicy.defaultMode(InputRegion.COMMENT));
        stringModeCombo.setSelectedItem(RegionPolicy.defaultMode(InputRegion.STRING));
        commitModeCombo.setSelectedItem(RegionPolicy.defaultMode(InputRegion.COMMIT));
        consoleModeCombo.setSelectedItem(RegionPolicy.defaultMode(InputRegion.CONSOLE));
        plainTextModeCombo.setSelectedItem(RegionPolicy.defaultMode(InputRegion.PLAIN_TEXT));
    }

    @Override
    public boolean isModified() {
        CharAutoReplaceSettings settings = CharAutoReplaceSettings.getInstance();
        CharAutoReplaceSettings.State state = settings.getState();

        if (showHintCheckBox.isSelected() != state.showHint) {
            return true;
        }
        if (codeModeCombo.getSelectedItem() != state.codeMode) return true;
        if (commentModeCombo.getSelectedItem() != state.commentMode) return true;
        if (stringModeCombo.getSelectedItem() != state.stringMode) return true;
        if (commitModeCombo.getSelectedItem() != state.commitMode) return true;
        if (consoleModeCombo.getSelectedItem() != state.consoleMode) return true;
        if (plainTextModeCombo.getSelectedItem() != state.plainTextMode) return true;

        List<MappingRule> currentRules = mappingTableModel.getRules();
        if (currentRules.size() != (state.customMappings == null ? 0 : state.customMappings.size())) {
            return true;
        }
        for (int i = 0; i < currentRules.size(); i++) {
            MappingRule a = currentRules.get(i);
            MappingRule b = state.customMappings.get(i);
            if (!a.equals(b)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply() {
        CharAutoReplaceSettings settings = CharAutoReplaceSettings.getInstance();
        CharAutoReplaceSettings.State state = settings.getState();

        state.customMappings = mappingTableModel.getRules();
        state.showHint = showHintCheckBox.isSelected();
        state.codeMode = (RegionPolicy.ReplaceMode) codeModeCombo.getSelectedItem();
        state.commentMode = (RegionPolicy.ReplaceMode) commentModeCombo.getSelectedItem();
        state.stringMode = (RegionPolicy.ReplaceMode) stringModeCombo.getSelectedItem();
        state.commitMode = (RegionPolicy.ReplaceMode) commitModeCombo.getSelectedItem();
        state.consoleMode = (RegionPolicy.ReplaceMode) consoleModeCombo.getSelectedItem();
        state.plainTextMode = (RegionPolicy.ReplaceMode) plainTextModeCombo.getSelectedItem();

        settings.loadState(state);
    }

    @Override
    public void reset() {
        CharAutoReplaceSettings settings = CharAutoReplaceSettings.getInstance();
        CharAutoReplaceSettings.State state = settings.getState();

        List<MappingRule> rules = state.customMappings == null
                ? new ArrayList<>()
                : new ArrayList<>(state.customMappings);
        mappingTableModel.setRules(rules);

        showHintCheckBox.setSelected(state.showHint);
        codeModeCombo.setSelectedItem(state.codeMode);
        commentModeCombo.setSelectedItem(state.commentMode);
        stringModeCombo.setSelectedItem(state.stringMode);
        commitModeCombo.setSelectedItem(state.commitMode);
        consoleModeCombo.setSelectedItem(state.consoleMode);
        plainTextModeCombo.setSelectedItem(state.plainTextMode);
    }

    // ==================================================================================
    // Mapping table model
    // ==================================================================================

    private static class MappingTableModel extends AbstractTableModel {
        private final List<MappingRule> rules = new ArrayList<>();
        private final String[] columnNames = {"源字符 (From)", "目标字符 (To)"};

        @Override
        public int getRowCount() {
            return rules.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MappingRule rule = rules.get(rowIndex);
            return columnIndex == 0 ? rule.from : rule.to;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String value = aValue == null ? "" : aValue.toString();
            MappingRule rule = rules.get(rowIndex);
            if (columnIndex == 0) {
                rule.from = value;
            } else {
                rule.to = value;
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        void addRow(MappingRule rule) {
            rules.add(rule);
            fireTableRowsInserted(rules.size() - 1, rules.size() - 1);
        }

        void removeRow(int row) {
            if (row >= 0 && row < rules.size()) {
                rules.remove(row);
                fireTableRowsDeleted(row, row);
            }
        }

        List<MappingRule> getRules() {
            List<MappingRule> result = new ArrayList<>();
            for (MappingRule rule : rules) {
                if (rule.from != null && !rule.from.isEmpty()) {
                    result.add(new MappingRule(rule.from, rule.to));
                }
            }
            return result;
        }

        void setRules(List<MappingRule> newRules) {
            rules.clear();
            rules.addAll(newRules);
            fireTableDataChanged();
        }
    }

    // ==================================================================================
    // ComboBox renderer for ReplaceMode
    // ==================================================================================

    private static class ModeListCellRenderer extends DefaultListCellRenderer {
        private final String[] labels;

        ModeListCellRenderer(String[] labels) {
            this.labels = labels;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RegionPolicy.ReplaceMode) {
                setText(labels[((RegionPolicy.ReplaceMode) value).ordinal()]);
            }
            return this;
        }
    }
}
