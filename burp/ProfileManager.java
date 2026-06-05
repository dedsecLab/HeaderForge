package burp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the list of {@link HeaderProfile}s and handles persistence
 * through the Burp project file ({@code saveExtensionSetting} /
 * {@code loadExtensionSetting}) as well as JSON import/export.
 */
public class ProfileManager {

    private final CopyOnWriteArrayList<HeaderProfile> profiles =
            new CopyOnWriteArrayList<>();
    private final IBurpExtenderCallbacks callbacks;

    public ProfileManager(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    // ------------------------------------------------------- list operations

    public List<HeaderProfile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    public HeaderProfile getProfile(int index) {
        return profiles.get(index);
    }

    public int size() {
        return profiles.size();
    }

    public void addProfile(HeaderProfile p) {
        profiles.add(p);
    }

    public void removeProfile(int index) {
        profiles.remove(index);
    }

    public void setProfile(int index, HeaderProfile p) {
        profiles.set(index, p);
    }

    // ====================================================================
    //  Burp project-file persistence
    // ====================================================================

    private static final String KEY_CONFIG = "ach_config_json";

    /** Persist all profiles into the Burp project file using chunked JSON. */
    public void saveToProject() {
        String json = exportToJson();
        saveLargeSetting(KEY_CONFIG, json);
    }

    /** Load profiles from the Burp project file. Returns true if any were found. */
    public boolean loadFromProject() {
        // Try new JSON chunked format first
        String json = loadLargeSetting(KEY_CONFIG);
        if (json != null && !json.trim().isEmpty()) {
            try {
                importFromJson(json);
                return true;
            } catch (JsonParseException e) {
                callbacks.printError("Failed to parse saved configuration: " + e.getMessage());
                return false;
            }
        }

        // Fallback to legacy field-by-field format
        String countStr = callbacks.loadExtensionSetting("ach_profile_count");
        if (countStr == null) return false;

        try {
            int count = Integer.parseInt(countStr);
            profiles.clear();
            for (int i = 0; i < count; i++) {
                String pfx = "ach_p" + i + "_";
                HeaderProfile p = new HeaderProfile();
                p.setName(loadOr(pfx + "name", p.getName()));
                p.setEnabled("true".equals(loadOr(pfx + "enabled", "true")));
                p.setHeaderName(loadOr(pfx + "headerName", p.getHeaderName()));
                p.setHeaderValuePrefix(loadOr(pfx + "headerValuePrefix", p.getHeaderValuePrefix()));
                p.setMode(loadOr(pfx + "mode", p.getMode()));
                p.setRegexpPattern(loadOr(pfx + "regexpPattern", p.getRegexpPattern()));
                p.setHardcodedValue(loadOr(pfx + "hardcodedValue", p.getHardcodedValue()));
                p.setScopePattern(loadOr(pfx + "scopePattern", p.getScopePattern()));
                p.setHttpMethods(loadOr(pfx + "httpMethods", p.getHttpMethods()));
                p.setOnlyIfNotExists("true".equals(loadOr(pfx + "onlyIfNotExists", "false")));
                profiles.add(p);
            }
            return !profiles.isEmpty();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void saveLargeSetting(String key, String value) {
        if (value == null) return;
        int chunkLen = 2000;
        int chunks = (value.length() + chunkLen - 1) / chunkLen;
        callbacks.saveExtensionSetting(key + "_count", String.valueOf(chunks));
        for (int i = 0; i < chunks; i++) {
            int start = i * chunkLen;
            int end = Math.min(start + chunkLen, value.length());
            callbacks.saveExtensionSetting(key + "_" + i, value.substring(start, end));
        }
        // Clean up extra chunks from previous saves
        for (int i = chunks; i < chunks + 100; i++) {
            if (callbacks.loadExtensionSetting(key + "_" + i) == null) break;
            callbacks.saveExtensionSetting(key + "_" + i, null);
        }
    }

    private String loadLargeSetting(String key) {
        String countStr = callbacks.loadExtensionSetting(key + "_count");
        if (countStr == null) return null;
        try {
            int chunks = Integer.parseInt(countStr);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < chunks; i++) {
                String chunk = callbacks.loadExtensionSetting(key + "_" + i);
                if (chunk != null) sb.append(chunk);
            }
            return sb.toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String loadOr(String key, String fallback) {
        String v = callbacks.loadExtensionSetting(key);
        return v != null ? v : fallback;
    }

    // ====================================================================
    //  JSON export / import   (no external dependencies)
    // ====================================================================

    /** Serialize all profiles to a pretty-printed JSON string. */
    public String exportToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"profiles\": [\n");
        for (int i = 0; i < profiles.size(); i++) {
            HeaderProfile p = profiles.get(i);
            sb.append("    {\n");
            jsonField(sb, "name",              p.getName(), true);
            jsonField(sb, "enabled",           p.isEnabled());
            jsonField(sb, "headerName",        p.getHeaderName(), true);
            jsonField(sb, "headerValuePrefix", p.getHeaderValuePrefix(), true);
            jsonField(sb, "mode",              p.getMode(), true);
            jsonField(sb, "regexpPattern",     p.getRegexpPattern(), true);
            jsonField(sb, "hardcodedValue",    p.getHardcodedValue(), true);
            jsonField(sb, "scopePattern",      p.getScopePattern(), true);
            jsonField(sb, "httpMethods",       p.getHttpMethods(), true);
            jsonFieldLast(sb, "onlyIfNotExists", p.isOnlyIfNotExists());
            sb.append("    }");
            if (i < profiles.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Parse a JSON string and replace the current profile list. */
    @SuppressWarnings("unchecked")
    public void importFromJson(String json) throws JsonParseException {
        Object root = new JsonParser(json).parse();
        if (!(root instanceof Map)) throw new JsonParseException("Root must be an object");

        Map<String, Object> rootMap = (Map<String, Object>) root;
        Object arr = rootMap.get("profiles");
        if (!(arr instanceof List)) throw new JsonParseException("Missing 'profiles' array");

        List<Object> list = (List<Object>) arr;
        List<HeaderProfile> imported = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) item;
            HeaderProfile p = new HeaderProfile();
            if (m.containsKey("name"))              p.setName(str(m, "name"));
            if (m.containsKey("enabled"))           p.setEnabled(boolVal(m, "enabled"));
            if (m.containsKey("headerName"))        p.setHeaderName(str(m, "headerName"));
            if (m.containsKey("headerValuePrefix")) p.setHeaderValuePrefix(str(m, "headerValuePrefix"));
            if (m.containsKey("mode"))              p.setMode(str(m, "mode"));
            if (m.containsKey("regexpPattern"))     p.setRegexpPattern(str(m, "regexpPattern"));
            if (m.containsKey("hardcodedValue"))    p.setHardcodedValue(str(m, "hardcodedValue"));
            if (m.containsKey("scopePattern"))      p.setScopePattern(str(m, "scopePattern"));
            if (m.containsKey("httpMethods"))       p.setHttpMethods(str(m, "httpMethods"));
            if (m.containsKey("onlyIfNotExists"))   p.setOnlyIfNotExists(boolVal(m, "onlyIfNotExists"));
            imported.add(p);
        }

        profiles.clear();
        profiles.addAll(imported);
    }

    // ----- JSON builder helpers ------------------------------------------

    private static void jsonField(StringBuilder sb, String key, String val, boolean hasNext) {
        sb.append("      \"").append(key).append("\": ").append(jsonStr(val));
        sb.append(hasNext ? ",\n" : "\n");
    }

    private static void jsonField(StringBuilder sb, String key, boolean val) {
        sb.append("      \"").append(key).append("\": ").append(val).append(",\n");
    }

    private static void jsonFieldLast(StringBuilder sb, String key, boolean val) {
        sb.append("      \"").append(key).append("\": ").append(val).append("\n");
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // ----- JSON reader helpers -------------------------------------------

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private static boolean boolVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    // ====================================================================
    //  Minimal recursive-descent JSON parser
    // ====================================================================

    public static class JsonParseException extends Exception {
        public JsonParseException(String msg) { super(msg); }
    }

    private static class JsonParser {
        private final String src;
        private int pos;

        JsonParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        Object parse() throws JsonParseException {
            skipWS();
            if (pos >= src.length()) throw err("Unexpected end of input");
            char c = src.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': case 'f': return parseBoolean();
                case 'n': return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
                    throw err("Unexpected character: " + c);
            }
        }

        private Map<String, Object> parseObject() throws JsonParseException {
            expect('{');
            Map<String, Object> map = new HashMap<>();
            skipWS();
            if (pos < src.length() && src.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWS();
                String key = parseString();
                skipWS();
                expect(':');
                Object val = parse();
                map.put(key, val);
                skipWS();
                if (pos >= src.length()) throw err("Unterminated object");
                if (src.charAt(pos) == '}') { pos++; return map; }
                expect(',');
            }
        }

        private List<Object> parseArray() throws JsonParseException {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWS();
            if (pos < src.length() && src.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                list.add(parse());
                skipWS();
                if (pos >= src.length()) throw err("Unterminated array");
                if (src.charAt(pos) == ']') { pos++; return list; }
                expect(',');
            }
        }

        private String parseString() throws JsonParseException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw err("Unterminated escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw err("Incomplete unicode escape");
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default: throw err("Unknown escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw err("Unterminated string");
        }

        private Boolean parseBoolean() throws JsonParseException {
            if (src.startsWith("true", pos))  { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw err("Expected boolean");
        }

        private Object parseNull() throws JsonParseException {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw err("Expected null");
        }

        private Number parseNumber() throws JsonParseException {
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') pos++;
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true; pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true; pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            String num = src.substring(start, pos);
            try {
                return isFloat ? Double.parseDouble(num) : Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw err("Invalid number: " + num);
            }
        }

        private void skipWS() {
            while (pos < src.length() && " \t\r\n".indexOf(src.charAt(pos)) >= 0) pos++;
        }

        private void expect(char c) throws JsonParseException {
            skipWS();
            if (pos >= src.length() || src.charAt(pos) != c)
                throw err("Expected '" + c + "'");
            pos++;
        }

        private JsonParseException err(String msg) {
            return new JsonParseException(msg + " at position " + pos);
        }
    }
}
