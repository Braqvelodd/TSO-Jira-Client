package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.LinkAction;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Vector;

public class LinkActionPanel extends JPanel {
    public interface LinkActionListener {
        void onMoveUp(LinkActionPanel panel);
        void onMoveDown(LinkActionPanel panel);
        void onRemove(LinkActionPanel panel);
    }

    private final LinkAction action;
    private final JCheckBox remoteToggle;
    private final JPanel modePanel;
    private final CardLayout cardLayout;
    private final JTextField inwardField;

    // Jira Link fields
    private final JTextField linkTypeField;
    private final JComboBox<String> linkTypeCombo;
    private final JTextField outwardField;

    // Remote Link fields
    private final JTextField remoteUrlField;
    private final JTextField remoteTitleField;
    private final JTextField remoteRelField;
    private final JTextField remoteSummaryField;

    public LinkActionPanel(LinkAction action, List<String> linkTypes, LinkActionListener listener) {
        this.action = action;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        JPanel main = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        // Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        JButton upBtn = new JButton("▲");
        JButton downBtn = new JButton("▼");
        JButton delBtn = new JButton("X");
        Dimension btnDim = new Dimension(22, 22);
        upBtn.setPreferredSize(btnDim); downBtn.setPreferredSize(btnDim); delBtn.setPreferredSize(btnDim);
        upBtn.setMargin(new Insets(0, 0, 0, 0)); downBtn.setMargin(new Insets(0, 0, 0, 0)); delBtn.setMargin(new Insets(0, 0, 0, 0));
        delBtn.setForeground(Color.RED);
        upBtn.addActionListener(e -> listener.onMoveUp(this));
        downBtn.addActionListener(e -> listener.onMoveDown(this));
        delBtn.addActionListener(e -> listener.onRemove(this));
        controls.add(upBtn); controls.add(downBtn); controls.add(delBtn);
        main.add(controls);

        remoteToggle = new JCheckBox("Remote?", action.isRemote());
        main.add(remoteToggle);

        inwardField = new JTextField(action.getInwardIssueToken(), 10);
        main.add(new JLabel("Inward:"));
        main.add(inwardField);

        modePanel = new JPanel();
        cardLayout = new CardLayout();
        modePanel.setLayout(cardLayout);

        // Jira Panel
        JPanel jiraPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        linkTypeField = new JTextField(action.getLinkType(), 10);
        linkTypeCombo = new JComboBox<>(new Vector<>(linkTypes));
        linkTypeCombo.setPreferredSize(new Dimension(100, 22));
        linkTypeCombo.addActionListener(e -> {
            String s = (String) linkTypeCombo.getSelectedItem();
            if (s != null && !s.isEmpty()) linkTypeField.setText(s);
        });
        outwardField = new JTextField(action.getOutwardIssueToken(), 10);
        jiraPanel.add(new JLabel("Type:")); jiraPanel.add(linkTypeField); jiraPanel.add(linkTypeCombo);
        jiraPanel.add(new JLabel("Outward:")); jiraPanel.add(outwardField);
        
        // Remote Panel
        JPanel remotePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        remoteUrlField = new JTextField(action.getUrl(), 15);
        remoteTitleField = new JTextField(action.getTitle(), 10);
        remoteRelField = new JTextField(action.getRelationship(), 8);
        remoteSummaryField = new JTextField(action.getSummary(), 10);
        remotePanel.add(new JLabel("URL:")); remotePanel.add(remoteUrlField);
        remotePanel.add(new JLabel("Title:")); remotePanel.add(remoteTitleField);
        remotePanel.add(new JLabel("Rel:")); remotePanel.add(remoteRelField);
        remotePanel.add(new JLabel("Summ:")); remotePanel.add(remoteSummaryField);

        modePanel.add(jiraPanel, "Jira");
        modePanel.add(remotePanel, "Remote");
        main.add(modePanel);

        add(main, BorderLayout.CENTER);

        remoteToggle.addActionListener(e -> updateModeUI());
        updateModeUI();

        tso.usmc.jira.util.JiraUtils.setupExpandedView(inwardField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(linkTypeField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(outwardField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(remoteUrlField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(remoteTitleField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(remoteRelField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(remoteSummaryField);
    }

    private void updateModeUI() {
        cardLayout.show(modePanel, remoteToggle.isSelected() ? "Remote" : "Jira");
    }

    public void saveToLinkAction() {
        action.setRemote(remoteToggle.isSelected());
        action.setInwardIssueToken(inwardField.getText());
        if (action.isRemote()) {
            action.setUrl(remoteUrlField.getText());
            action.setTitle(remoteTitleField.getText());
            action.setRelationship(remoteRelField.getText());
            action.setSummary(remoteSummaryField.getText());
        } else {
            action.setLinkType(linkTypeField.getText());
            action.setOutwardIssueToken(outwardField.getText());
        }
    }

    public LinkAction getLinkAction() {
        saveToLinkAction();
        return action;
    }

    public void updateLinkTypes(List<String> linkTypes) {
        String current = (String) linkTypeCombo.getSelectedItem();
        linkTypeCombo.removeAllItems();
        linkTypeCombo.addItem("");
        for (String lt : linkTypes) linkTypeCombo.addItem(lt);
        if (current != null) linkTypeCombo.setSelectedItem(current);
    }
}
