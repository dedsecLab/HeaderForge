package burp;

import java.awt.Component;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Main extension entry-point.
 * <p>
 * Implements:
 * <ul>
 *   <li>{@code IBurpExtender}            – lifecycle</li>
 *   <li>{@code ISessionHandlingAction}   – header injection</li>
 *   <li>{@code ITab}                     – UI tab</li>
 *   <li>{@code IExtensionStateListener}  – save on unload</li>
 *   <li>{@code IContextMenuFactory}      – right-click "Use as header value"</li>
 * </ul>
 */
public class BurpExtender implements IBurpExtender, ISessionHandlingAction,
        ITab, IExtensionStateListener, IContextMenuFactory {

    static final String EXTENSION_NAME = "HeaderForge";
    static final String AUTHOR = "dedsecLab";

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;

    private ProfileManager    profileManager;
    private ProfileTableModel profileTableModel;
    private LogTableModel     logTableModel;
    private MainPanel         mainPanel;

    // ================================================================ lifecycle

    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers   = callbacks.getHelpers();
        callbacks.setExtensionName(EXTENSION_NAME);
        callbacks.registerSessionHandlingAction(this);
        callbacks.registerExtensionStateListener(this);
        callbacks.registerContextMenuFactory(this);

        // -- load / initialize profiles
        profileManager = new ProfileManager(callbacks);
        if (!profileManager.loadFromProject()) {
            // First run: create one default profile
            HeaderProfile def = new HeaderProfile();
            def.setName("Default");
            def.setHeaderName("Authorization");
            def.setHeaderValuePrefix("Bearer ");
            def.setRegexpPattern("access_token\\\":\\\"(.*?)\\\"");
            def.setHardcodedValue("");
            def.setMode("disabled");
            profileManager.addProfile(def);
        }

        profileTableModel = new ProfileTableModel(profileManager);
        logTableModel     = new LogTableModel();

        // -- build UI on the EDT
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                mainPanel = new MainPanel(profileManager, profileTableModel,
                        logTableModel, callbacks);

                // auto-save whenever anything changes in the UI
                mainPanel.setOnSettingsChanged(new Runnable() {
                    @Override
                    public void run() { profileManager.saveToProject(); }
                });

                callbacks.customizeUiComponent(mainPanel);
                callbacks.addSuiteTab(BurpExtender.this);
            }
        });

        callbacks.printOutput("========================================");
        callbacks.printOutput("  " + EXTENSION_NAME + " v2.0");
        callbacks.printOutput("  Author: " + AUTHOR);
        callbacks.printOutput("  Profiles loaded: " + profileManager.size());
        callbacks.printOutput("========================================");
    }

    @Override
    public void extensionUnloaded() {
        profileManager.saveToProject();
        callbacks.printOutput(EXTENSION_NAME + " unloaded — settings saved");
    }

    // ================================================================ ITab

    @Override public String getTabCaption()    { return EXTENSION_NAME; }
    @Override public Component getUiComponent(){ return mainPanel; }

    // ================================================================ ISessionHandlingAction

    @Override
    public String getActionName() { return EXTENSION_NAME; }

    @Override
    public void performAction(IHttpRequestResponse currentRequest,
                              IHttpRequestResponse[] macroItems) {

        IRequestInfo rqInfo = helpers.analyzeRequest(currentRequest);
        String method = rqInfo.getMethod();
        URL    url    = rqInfo.getUrl();
        String fullUrl = url != null ? url.toString() : "";

        // snapshot the profile list (CopyOnWriteArrayList – safe iterator)
        List<HeaderProfile> profiles = profileManager.getProfiles();

        for (HeaderProfile p : profiles) {
            if (!p.isEnabled() || p.isDisabled()) continue;

            // ---- condition: scope URL pattern
            if (!matchesScope(p.getScopePattern(), fullUrl)) continue;

            // ---- condition: HTTP methods
            if (!matchesMethods(p.getHttpMethods(), method)) continue;

            // ---- condition: header already exists
            ArrayList<String> headers =
                    (ArrayList<String>) helpers.analyzeRequest(currentRequest).getHeaders();
            if (p.isOnlyIfNotExists() && headerExists(headers, p.getHeaderName())) continue;

            // ---- determine the value
            String token = null;
            String source = null;

            if (p.isHardcoded()) {
                token  = p.getHardcodedValue();
                source = "hardcoded";
            } else if (p.isRegexp()) {
                token  = extractFromMacro(p.getRegexpPattern(), macroItems);
                source = "regexp";
            } else if (p.isCookie()) {
                token  = extractCookie((ArrayList<String>) helpers.analyzeRequest(currentRequest).getHeaders(), p.getCookieName());
                source = "cookie";
            }

            if (token == null || token.isEmpty()) {
                callbacks.printError("[" + p.getName() + "] No token found");
                continue;
            }

            // ---- re-read headers (they may have been modified by a previous profile)
            rqInfo  = helpers.analyzeRequest(currentRequest);
            headers = (ArrayList<String>) rqInfo.getHeaders();

            // remove ALL existing instances of this header (by name, case-insensitive)
            // so that e.g. "Authorization: Basic xyz" is replaced by "Authorization: Bearer <token>"
            String matchPrefix = p.getHeaderName().toLowerCase() + ":";
            for (int i = headers.size() - 1; i >= 0; i--) {
                if (headers.get(i).toLowerCase().startsWith(matchPrefix)) {
                    headers.remove(i);
                }
            }

            String newHeader = p.getHeaderName() + ": " + p.getHeaderValuePrefix() + token;
            headers.add(newHeader);

            byte[] body = Arrays.copyOfRange(
                    currentRequest.getRequest(),
                    rqInfo.getBodyOffset(),
                    currentRequest.getRequest().length);
            currentRequest.setRequest(helpers.buildHttpMessage(headers, body));

            callbacks.printOutput("[" + EXTENSION_NAME + "] [" + p.getName() + "] Injected: " + newHeader);

            // ---- log
            logTableModel.addEntry(new LogEntry(
                    fullUrl, method, p.getName(),
                    p.getHeaderName(), p.getHeaderValuePrefix() + token, source));
        }
    }

    // ================================================================ IContextMenuFactory

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        // Only show when text is selected in a request/response viewer/editor
        final int[] bounds = invocation.getSelectionBounds();
        if (bounds == null || bounds[0] == bounds[1]) return null;

        byte ctx = invocation.getInvocationContext();
        boolean isRequest  = (ctx == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST
                           || ctx == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST);
        boolean isResponse = (ctx == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE
                           || ctx == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_RESPONSE);
        if (!isRequest && !isResponse) return null;

        final IHttpRequestResponse[] messages = invocation.getSelectedMessages();
        if (messages == null || messages.length == 0) return null;

        // extract selected text
        byte[] data = isRequest ? messages[0].getRequest() : messages[0].getResponse();
        if (data == null) return null;
        final String selected = helpers.bytesToString(
                Arrays.copyOfRange(data, bounds[0], bounds[1]));
        if (selected.isEmpty()) return null;

        // build submenu with one item per profile
        JMenu menu = new JMenu("Use as Custom Header Value");
        List<HeaderProfile> profiles = profileManager.getProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            final int idx = i;
            HeaderProfile p = profiles.get(i);
            JMenuItem item = new JMenuItem(p.getName() + "  (" + p.getHeaderName() + ")");
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            mainPanel.setHardcodedValueForProfile(idx, selected);
                            callbacks.printOutput("Set hardcoded value for '"
                                    + profileManager.getProfile(idx).getName()
                                    + "' from context menu");
                        }
                    });
                }
            });
            menu.add(item);
        }

        List<JMenuItem> items = new ArrayList<>();
        items.add(menu);
        return items;
    }

    // ================================================================ helpers

    private String extractFromMacro(String regexp, IHttpRequestResponse[] macroItems) {
        if (macroItems == null || macroItems.length == 0) {
            callbacks.issueAlert("No macro configured or macro returned no response");
            return null;
        }
        Pattern p;
        try {
            p = Pattern.compile(regexp);
        } catch (PatternSyntaxException e) {
            callbacks.issueAlert("Regex syntax error: " + e.getMessage());
            callbacks.printError(e.toString());
            return null;
        }
        for (IHttpRequestResponse macro : macroItems) {
            byte[] resp = macro.getResponse();
            if (resp == null) continue;
            String body = helpers.bytesToString(resp);
            Matcher m = p.matcher(body);
            if (m.find()) {
                String token = m.group(1);
                if (token != null && !token.isEmpty()) return token;
            }
        }
        return null;
    }

    private String extractCookie(List<String> headers, String cookieName) {
        if (cookieName == null || cookieName.isEmpty()) return null;
        String prefix = cookieName + "=";
        for (String header : headers) {
            if (header.toLowerCase().startsWith("cookie:")) {
                String cookieString = header.substring(7);
                String[] cookies = cookieString.split(";");
                for (String cookie : cookies) {
                    cookie = cookie.trim();
                    if (cookie.startsWith(prefix)) {
                        return cookie.substring(prefix.length());
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesScope(String scopePattern, String url) {
        if (scopePattern == null || scopePattern.isEmpty()) return true;
        try {
            return Pattern.compile(scopePattern).matcher(url).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static boolean matchesMethods(String allowedMethods, String method) {
        if (allowedMethods == null || allowedMethods.trim().isEmpty()) return true;
        for (String m : allowedMethods.split(",")) {
            if (m.trim().equalsIgnoreCase(method)) return true;
        }
        return false;
    }

    private static boolean headerExists(List<String> headers, String headerName) {
        String prefix = headerName + ":";
        for (String h : headers) {
            if (h.toLowerCase().startsWith(prefix.toLowerCase())) return true;
        }
        return false;
    }
}
