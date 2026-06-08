package burp;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * Swing table model for the profile list.
 * Columns: Enabled (checkbox) · Name · Header Name · Mode
 */
public class ProfileTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Enabled", "Name", "Header Name", "Mode"};
    private static final Class<?>[] COL_TYPES = {Boolean.class, String.class, String.class, String.class};

    private final ProfileManager manager;

    public ProfileTableModel(ProfileManager manager) {
        this.manager = manager;
    }

    @Override public int getRowCount()    { return manager.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }
    @Override public Class<?> getColumnClass(int col) { return COL_TYPES[col]; }

    @Override
    public Object getValueAt(int row, int col) {
        HeaderProfile p = manager.getProfile(row);
        switch (col) {
            case 0: return p.isEnabled();
            case 1: return p.getName();
            case 2: return p.getHeaderName();
            case 3: return friendlyMode(p.getMode());
            default: return null;
        }
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col == 0;   // only the "Enabled" checkbox is editable in-table
    }

    @Override
    public void setValueAt(Object val, int row, int col) {
        if (col == 0 && val instanceof Boolean) {
            manager.getProfile(row).setEnabled((Boolean) val);
            fireTableCellUpdated(row, col);
        }
    }

    /** Notify the table that data has changed externally. */
    public void refresh() {
        fireTableDataChanged();
    }

    private static String friendlyMode(String mode) {
        if ("regexp".equals(mode))    return "RegExp";
        if ("hardcoded".equals(mode)) return "Hard-Coded";
        if ("cookie".equals(mode))    return "Cookie";
        return "Disabled";
    }
}
