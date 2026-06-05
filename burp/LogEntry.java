package burp;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Immutable record of a single header-injection event.
 */
public class LogEntry {

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm:ss.SSS");

    private final long   timestamp;
    private final String url;
    private final String method;
    private final String profileName;
    private final String headerName;
    private final String headerValue;
    private final String source;      // "regexp" | "hardcoded"

    public LogEntry(String url, String method, String profileName,
                    String headerName, String headerValue, String source) {
        this.timestamp   = System.currentTimeMillis();
        this.url         = url;
        this.method      = method;
        this.profileName = profileName;
        this.headerName  = headerName;
        this.headerValue = headerValue;
        this.source      = source;
    }

    // ----------------------------------------------------------- accessors

    public long   getTimestamp()   { return timestamp; }
    public String getUrl()        { return url; }
    public String getMethod()     { return method; }
    public String getProfileName(){ return profileName; }
    public String getHeaderName() { return headerName; }
    public String getHeaderValue(){ return headerValue; }
    public String getSource()     { return source; }

    public String getFormattedTime() {
        synchronized (TIME_FMT) {           // SimpleDateFormat is not thread-safe
            return TIME_FMT.format(new Date(timestamp));
        }
    }
}
