package burp;

/**
 * Data model for a single header-injection profile.
 * Each profile defines one header to inject, the source of its value,
 * and the conditions under which injection should occur.
 */
public class HeaderProfile {

    private String name;
    private boolean enabled;
    private String headerName;
    private String headerValuePrefix;
    /** "disabled", "regexp", or "hardcoded" */
    private String mode;
    private String regexpPattern;
    private String hardcodedValue;
    /** URL regex — empty string means "match all URLs". */
    private String scopePattern;
    /** Comma-separated HTTP methods — empty means "all methods". */
    private String httpMethods;
    /** When true, the header is injected only if it is not already present. */
    private boolean onlyIfNotExists;

    // ------------------------------------------------------------------ ctors

    /** Creates a blank profile with sensible defaults. */
    public HeaderProfile() {
        this.name = "New Profile";
        this.enabled = true;
        this.headerName = "Authorization";
        this.headerValuePrefix = "Bearer ";
        this.mode = "disabled";
        this.regexpPattern = "access_token\\\":\\\"(.*?)\\\"";
        this.hardcodedValue = "";
        this.scopePattern = "";
        this.httpMethods = "";
        this.onlyIfNotExists = false;
    }

    /** Copy constructor (used by "Duplicate" action). */
    public HeaderProfile(HeaderProfile other) {
        this.name = other.name + " (copy)";
        this.enabled = other.enabled;
        this.headerName = other.headerName;
        this.headerValuePrefix = other.headerValuePrefix;
        this.mode = other.mode;
        this.regexpPattern = other.regexpPattern;
        this.hardcodedValue = other.hardcodedValue;
        this.scopePattern = other.scopePattern;
        this.httpMethods = other.httpMethods;
        this.onlyIfNotExists = other.onlyIfNotExists;
    }

    // -------------------------------------------------------------- accessors

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public String getHeaderValuePrefix() { return headerValuePrefix; }
    public void setHeaderValuePrefix(String headerValuePrefix) { this.headerValuePrefix = headerValuePrefix; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getRegexpPattern() { return regexpPattern; }
    public void setRegexpPattern(String regexpPattern) { this.regexpPattern = regexpPattern; }

    public String getHardcodedValue() { return hardcodedValue; }
    public void setHardcodedValue(String hardcodedValue) { this.hardcodedValue = hardcodedValue; }

    public String getScopePattern() { return scopePattern; }
    public void setScopePattern(String scopePattern) { this.scopePattern = scopePattern; }

    public String getHttpMethods() { return httpMethods; }
    public void setHttpMethods(String httpMethods) { this.httpMethods = httpMethods; }

    public boolean isOnlyIfNotExists() { return onlyIfNotExists; }
    public void setOnlyIfNotExists(boolean onlyIfNotExists) { this.onlyIfNotExists = onlyIfNotExists; }

    // --------------------------------------------------------------- helpers

    public boolean isDisabled() { return "disabled".equals(mode); }
    public boolean isRegexp()   { return "regexp".equals(mode); }
    public boolean isHardcoded(){ return "hardcoded".equals(mode); }

    @Override
    public String toString() {
        return name;
    }
}
