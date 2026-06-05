package burp;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Top-level panel shown in the Burp tab.
 * Contains two sub-tabs: <b>Profiles</b> and <b>Log</b>.
 */
public class MainPanel extends JPanel {

    // -- models
    private final ProfileManager    profileManager;
    private final ProfileTableModel profileTableModel;
    private final LogTableModel     logTableModel;
    private final IBurpExtenderCallbacks callbacks;

    // -- profile table
    private JTable profileTable;

    // -- profile edit form widgets
    private JTextField nameField;
    private JTextField headerNameField;
    private JTextField headerPrefixField;
    private JRadioButton disabledRadio;
    private JRadioButton regexpRadio;
    private JRadioButton hardcodedRadio;
    private JTextField regexpField;
    private JTextArea  hardcodedArea;
    private JTextField scopeField;
    private JTextField methodsField;
    private JCheckBox  onlyIfNotExistsCb;
    private JLabel     previewLabel;

    /** True while we are loading a profile into the form (suppresses save). */
    private boolean updatingForm = false;

    // ================================================================ ctor

    public MainPanel(ProfileManager profileManager,
                     ProfileTableModel profileTableModel,
                     LogTableModel logTableModel,
                     IBurpExtenderCallbacks callbacks) {
        this.profileManager    = profileManager;
        this.profileTableModel = profileTableModel;
        this.logTableModel     = logTableModel;
        this.callbacks         = callbacks;

        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Profiles", buildProfilesTab());
        tabs.addTab("Log",      buildLogTab());
        add(tabs, BorderLayout.CENTER);

        // Select the first profile if available
        if (profileManager.size() > 0) {
            profileTable.setRowSelectionInterval(0, 0);
            loadProfileIntoForm(0);
        }
    }

    // =============================================================== Profiles tab

    private JPanel buildProfilesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // -- top: profile table + buttons
        JPanel topPanel = new JPanel(new BorderLayout(0, 4));
        topPanel.setBorder(titledBorder("Header Profiles"));

        profileTable = new JTable(profileTableModel);
        profileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileTable.getColumnModel().getColumn(0).setMaxWidth(60);
        profileTable.getColumnModel().getColumn(0).setMinWidth(60);
        profileTable.setRowHeight(22);
        profileTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int row = profileTable.getSelectedRow();
                    if (row >= 0) loadProfileIntoForm(row);
                }
            }
        });
        // listen for the enabled-checkbox toggle in the table
        profileTableModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE) {
                    onSettingsChanged();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(profileTable);
        tableScroll.setPreferredSize(new Dimension(0, 150));
        topPanel.add(tableScroll, BorderLayout.CENTER);
        topPanel.add(buildButtonBar(), BorderLayout.SOUTH);

        // -- bottom: profile editor
        JPanel editorPanel = buildProfileEditor();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, editorPanel);
        split.setResizeWeight(0.35);
        split.setDividerSize(6);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    // ---- button bar under the table ------------------------------------

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onAdd(); }
        });

        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onRemove(); }
        });

        JButton dupBtn = new JButton("Duplicate");
        dupBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onDuplicate(); }
        });

        // spacer
        JPanel spacer = new JPanel();
        spacer.setPreferredSize(new Dimension(30, 1));

        JButton importBtn = new JButton("Import JSON");
        importBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onImport(); }
        });

        JButton exportBtn = new JButton("Export JSON");
        exportBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onExport(); }
        });

        bar.add(addBtn);
        bar.add(removeBtn);
        bar.add(dupBtn);
        bar.add(spacer);
        bar.add(importBtn);
        bar.add(exportBtn);
        return bar;
    }

    // ---- profile editor form -------------------------------------------

    private JPanel buildProfileEditor() {
        JPanel editor = new JPanel(new GridBagLayout());
        editor.setBorder(titledBorder("Profile Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // -- Name
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("Profile Name:"), gbc);
        nameField = new JTextField(20);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        editor.add(nameField, gbc);
        gbc.gridwidth = 1; gbc.weightx = 0;
        row++;

        // -- Header Name + Prefix
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("Header Name:"), gbc);
        headerNameField = new JTextField(14);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(headerNameField, gbc);

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("Value Prefix:"), gbc);
        headerPrefixField = new JTextField(14);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(headerPrefixField, gbc);
        row++;

        // -- separator
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        row++;

        // -- Value source radio buttons
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("Value Source:"), gbc);

        ButtonGroup modeGroup = new ButtonGroup();
        disabledRadio  = new JRadioButton("Disabled");
        regexpRadio    = new JRadioButton("Regular Expression");
        hardcodedRadio = new JRadioButton("Hard-Coded Value");
        modeGroup.add(disabledRadio);
        modeGroup.add(regexpRadio);
        modeGroup.add(hardcodedRadio);
        disabledRadio.setSelected(true);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        radioPanel.add(disabledRadio);
        radioPanel.add(regexpRadio);
        radioPanel.add(hardcodedRadio);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(radioPanel, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- RegExp field
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("RegExp:"), gbc);
        regexpField = new JTextField(30);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(regexpField, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- Hard-coded value
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        editor.add(new JLabel("Hard-Coded:"), gbc);
        gbc.anchor = GridBagConstraints.WEST;
        hardcodedArea = new JTextArea(3, 30);
        hardcodedArea.setLineWrap(true);
        JScrollPane hcScroll = new JScrollPane(hardcodedArea);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(hcScroll, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- separator
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        row++;

        // -- Conditions heading
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4; gbc.fill = GridBagConstraints.NONE;
        JLabel condLabel = new JLabel("Conditions (leave blank to match all)");
        condLabel.setFont(condLabel.getFont().deriveFont(Font.BOLD));
        editor.add(condLabel, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- Scope URL pattern
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("Scope (URL regex):"), gbc);
        scopeField = new JTextField(30);
        scopeField.setToolTipText("Java regex matched against the full request URL. Example: https://api\\.example\\.com/.*");
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(scopeField, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- HTTP methods
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("HTTP Methods:"), gbc);
        methodsField = new JTextField(20);
        methodsField.setToolTipText("Comma-separated, e.g.: GET,POST,PUT. Leave blank for all.");
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(methodsField, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- Only if not exists
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.NONE;
        onlyIfNotExistsCb = new JCheckBox("Only inject if header does not already exist");
        editor.add(onlyIfNotExistsCb, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- separator
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        row++;

        // -- preview
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        editor.add(new JLabel("Preview:"), gbc);
        previewLabel = new JLabel(" ");
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.ITALIC));
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        editor.add(previewLabel, gbc);
        gbc.gridwidth = 1;
        row++;

        // -- filler to push content up
        gbc.gridx = 0; gbc.gridy = row; gbc.weighty = 1;
        gbc.gridwidth = 4; gbc.fill = GridBagConstraints.BOTH;
        editor.add(new JPanel(), gbc);

        // -- wire change listeners
        wireFormListeners();

        return editor;
    }

    // ---- change listeners ------------------------------------------------

    private void wireFormListeners() {
        DocumentListener docListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onFormChanged(); }
            @Override public void removeUpdate(DocumentEvent e)  { onFormChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onFormChanged(); }
        };
        nameField.getDocument().addDocumentListener(docListener);
        headerNameField.getDocument().addDocumentListener(docListener);
        headerPrefixField.getDocument().addDocumentListener(docListener);
        regexpField.getDocument().addDocumentListener(docListener);
        hardcodedArea.getDocument().addDocumentListener(docListener);
        scopeField.getDocument().addDocumentListener(docListener);
        methodsField.getDocument().addDocumentListener(docListener);

        ActionListener radioListener = new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onFormChanged(); }
        };
        disabledRadio.addActionListener(radioListener);
        regexpRadio.addActionListener(radioListener);
        hardcodedRadio.addActionListener(radioListener);

        onlyIfNotExistsCb.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onFormChanged(); }
        });
    }

    private void onFormChanged() {
        if (updatingForm) return;
        int row = profileTable.getSelectedRow();
        if (row < 0) return;

        HeaderProfile p = profileManager.getProfile(row);
        saveFormToProfile(p);
        profileTableModel.fireTableRowsUpdated(row, row);
        updatePreview();
        onSettingsChanged();
    }

    // ---- load / save between form and profile ---------------------------

    private void loadProfileIntoForm(int index) {
        if (index < 0 || index >= profileManager.size()) return;
        updatingForm = true;
        try {
            HeaderProfile p = profileManager.getProfile(index);
            nameField.setText(p.getName());
            headerNameField.setText(p.getHeaderName());
            headerPrefixField.setText(p.getHeaderValuePrefix());
            regexpField.setText(p.getRegexpPattern());
            hardcodedArea.setText(p.getHardcodedValue());
            scopeField.setText(p.getScopePattern());
            methodsField.setText(p.getHttpMethods());
            onlyIfNotExistsCb.setSelected(p.isOnlyIfNotExists());

            switch (p.getMode()) {
                case "regexp":    regexpRadio.setSelected(true); break;
                case "hardcoded": hardcodedRadio.setSelected(true); break;
                default:          disabledRadio.setSelected(true);
            }
            updatePreview();
        } finally {
            updatingForm = false;
        }
    }

    private void saveFormToProfile(HeaderProfile p) {
        p.setName(nameField.getText());
        p.setHeaderName(headerNameField.getText());
        p.setHeaderValuePrefix(headerPrefixField.getText());
        p.setRegexpPattern(regexpField.getText());
        p.setHardcodedValue(hardcodedArea.getText());
        p.setScopePattern(scopeField.getText());
        p.setHttpMethods(methodsField.getText());
        p.setOnlyIfNotExists(onlyIfNotExistsCb.isSelected());

        if (regexpRadio.isSelected())         p.setMode("regexp");
        else if (hardcodedRadio.isSelected()) p.setMode("hardcoded");
        else                                  p.setMode("disabled");
    }

    private void updatePreview() {
        String hdr = headerNameField.getText() + ": " + headerPrefixField.getText();
        if (hardcodedRadio.isSelected()) {
            hdr += hardcodedArea.getText();
        } else if (regexpRadio.isSelected()) {
            hdr += "[regexp: " + regexpField.getText() + "]";
        } else {
            hdr += "(disabled)";
        }
        previewLabel.setText(hdr);
    }

    // ---- button actions -------------------------------------------------

    private void onAdd() {
        HeaderProfile p = new HeaderProfile();
        p.setName("Profile " + (profileManager.size() + 1));
        profileManager.addProfile(p);
        profileTableModel.refresh();
        int idx = profileManager.size() - 1;
        profileTable.setRowSelectionInterval(idx, idx);
        loadProfileIntoForm(idx);
        onSettingsChanged();
    }

    private void onRemove() {
        int row = profileTable.getSelectedRow();
        if (row < 0) return;
        profileManager.removeProfile(row);
        profileTableModel.refresh();
        if (profileManager.size() > 0) {
            int sel = Math.min(row, profileManager.size() - 1);
            profileTable.setRowSelectionInterval(sel, sel);
            loadProfileIntoForm(sel);
        }
        onSettingsChanged();
    }

    private void onDuplicate() {
        int row = profileTable.getSelectedRow();
        if (row < 0) return;
        HeaderProfile dup = new HeaderProfile(profileManager.getProfile(row));
        profileManager.addProfile(dup);
        profileTableModel.refresh();
        int idx = profileManager.size() - 1;
        profileTable.setRowSelectionInterval(idx, idx);
        loadProfileIntoForm(idx);
        onSettingsChanged();
    }

    private void onImport() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import Header Profiles");
        fc.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(fc.getSelectedFile()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();

            profileManager.importFromJson(sb.toString());
            profileTableModel.refresh();
            if (profileManager.size() > 0) {
                profileTable.setRowSelectionInterval(0, 0);
                loadProfileIntoForm(0);
            }
            onSettingsChanged();
            JOptionPane.showMessageDialog(this,
                    "Imported " + profileManager.size() + " profile(s).",
                    "Import Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (ProfileManager.JsonParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid JSON: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "File read error: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onExport() {
        if (profileManager.size() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No profiles to export.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Header Profiles");
        fc.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        fc.setSelectedFile(new File("custom-header-profiles.json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().endsWith(".json")) {
            file = new File(file.getAbsolutePath() + ".json");
        }

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(profileManager.exportToJson());
            writer.close();
            JOptionPane.showMessageDialog(this,
                    "Exported " + profileManager.size() + " profile(s) to " + file.getName(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "File write error: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =============================================================== Log tab

    private JPanel buildLogTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTable logTable = new JTable(logTableModel);
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(90);  // Time
        logTable.getColumnModel().getColumn(1).setPreferredWidth(60);  // Method
        logTable.getColumnModel().getColumn(2).setPreferredWidth(300); // URL
        logTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Profile
        logTable.getColumnModel().getColumn(4).setPreferredWidth(110); // Header
        logTable.getColumnModel().getColumn(5).setPreferredWidth(200); // Value
        logTable.getColumnModel().getColumn(6).setPreferredWidth(80);  // Source
        logTable.setRowHeight(20);

        JScrollPane scroll = new JScrollPane(logTable);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton clearBtn = new JButton("Clear Log");
        clearBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { logTableModel.clear(); }
        });
        bottomBar.add(clearBtn);
        panel.add(bottomBar, BorderLayout.SOUTH);

        return panel;
    }

    // =============================================================== persistence callback

    private Runnable settingsChangedCallback;

    /** Set by BurpExtender so any UI change triggers a project save. */
    public void setOnSettingsChanged(Runnable callback) {
        this.settingsChangedCallback = callback;
    }

    private void onSettingsChanged() {
        if (settingsChangedCallback != null) {
            settingsChangedCallback.run();
        }
    }

    // =============================================================== public API for context menu

    /**
     * Called from the context-menu handler to set a hardcoded value
     * on a specific profile (by index) and switch it to hardcoded mode.
     */
    public void setHardcodedValueForProfile(int profileIndex, String value) {
        if (profileIndex < 0 || profileIndex >= profileManager.size()) return;
        HeaderProfile p = profileManager.getProfile(profileIndex);
        p.setHardcodedValue(value);
        p.setMode("hardcoded");
        // If this profile is currently selected, refresh the form
        if (profileTable.getSelectedRow() == profileIndex) {
            loadProfileIntoForm(profileIndex);
        }
        profileTableModel.refresh();
        onSettingsChanged();
    }

    // =============================================================== utility

    private static TitledBorder titledBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title);
    }
}
