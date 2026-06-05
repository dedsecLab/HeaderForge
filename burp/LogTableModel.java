package burp;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing table model for the request-injection log.
 * Columns: Time · Method · URL · Profile · Header · Value · Source
 * <p>
 * Thread-safe: entries may be added from any thread and the model
 * fires table-change events on the EDT.
 */
public class LogTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Time", "Method", "URL", "Profile", "Header", "Value", "Source"
    };

    private final List<LogEntry> entries = new ArrayList<>();
    private int maxEntries = 500;

    @Override public int getRowCount()    { return entries.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Object getValueAt(int row, int col) {
        LogEntry e = entries.get(row);
        switch (col) {
            case 0: return e.getFormattedTime();
            case 1: return e.getMethod();
            case 2: return e.getUrl();
            case 3: return e.getProfileName();
            case 4: return e.getHeaderName();
            case 5: return e.getHeaderValue();
            case 6: return e.getSource();
            default: return null;
        }
    }

    /** Add an entry (may be called from any thread). */
    public void addEntry(final LogEntry entry) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                entries.add(0, entry);   // newest first
                fireTableRowsInserted(0, 0);
                // trim excess
                while (entries.size() > maxEntries) {
                    int last = entries.size() - 1;
                    entries.remove(last);
                    fireTableRowsDeleted(last, last);
                }
            }
        });
    }

    /** Remove all entries. */
    public void clear() {
        int sz = entries.size();
        if (sz == 0) return;
        entries.clear();
        fireTableRowsDeleted(0, sz - 1);
    }

    public int getMaxEntries() { return maxEntries; }

    public void setMaxEntries(int max) {
        this.maxEntries = Math.max(10, max);
    }
}
