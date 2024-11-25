package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kbws
 * @date 2024/6/13
 * @description: Request请求对应的HTTP协议接口定义
 */
public class HttpRequest implements HttpServletRequest {

    private static final Logger log = LoggerFactory.getLogger(HttpRequest.class);


    private InputStream input;
    private SocketInputStream sis;
    private String uri;
    private String queryString;
    InetAddress address;
    int port;
    private boolean parsed = false;
    // 保存头部信息和参数
    protected HashMap<String, String> headers = new HashMap<>();
    protected Map<String, String[]> parameters = new ConcurrentHashMap<>();
    HttpRequestLine requestLine = new HttpRequestLine();
    Cookie[] cookies;
    HttpSession session;
    String sessionId;
    SessionFacade sessionFacade;

    public HttpRequest(InputStream input) {
        this.input = input;
        this.sis = new SocketInputStream(this.input, 2048);
    }

    public void parse(Socket socket) {
        try {
            parseConnection(socket);
            this.sis.readRequestLine(requestLine);
            parseRequestLine();
            parseHeaders();
        } catch (IOException | ServletException e) {
            log.error("error: ", e);
        }
    }

    private void parseConnection(Socket socket) {
        address = socket.getInetAddress();
        port = socket.getPort();
    }

    // 解析所有header信息
    private void parseHeaders() throws IOException, ServletException {
        while (true) {
            HttpHeader header = new HttpHeader();
            sis.readHeader(header);
            if (header.nameEnd == 0) {
                if (header.valueEnd == 0) {
                    return;
                } else {
                    throw new ServletException("httpProcessor.parseHeaders.colon");
                }
            }
            String name = new String(header.name, 0, header.nameEnd);
            String value = new String(header.value, 0, header.valueEnd);
            // Set the corresponding request headers
            if (name.equals(DefaultHeaders.ACCEPT_LANGUAGE_NAME)) {
                headers.put(name, value);
            } else if (name.equals(DefaultHeaders.CONTENT_LENGTH_NAME)) {
                headers.put(name, value);
            } else if (name.equals(DefaultHeaders.CONTENT_TYPE_NAME)) {
                headers.put(name, value);
            } else if (name.equals(DefaultHeaders.HOST_NAME)) {
                headers.put(name, value);
            } else if (name.equals(DefaultHeaders.CONNECTION_NAME)) {
                headers.put(name, value);
            } else if (name.equals(DefaultHeaders.TRANSFER_ENCODING_NAME)) {
                headers.put(name, value);
            } else if (name.equals(DefaultHeaders.COOKIE_NAME)) {
                headers.put(name, value);
                //处理cookie和session
                Cookie[] cookieArr = parseCookieHeader(value);
                this.cookies = cookieArr;
                for (int i = 0; i < cookies.length; i++) {
                    if (cookies[i].getName().equals("jsessionid")) {
                        this.sessionId = cookies[i].getValue();
                    }
                }
            } else {
                headers.put(name, value);
            }
        }
    }

    public Cookie[] parseCookieHeader(String header) {
        if ((header == null) || (header.isEmpty()) )
            return (new Cookie[0]);
        ArrayList<Cookie> cookieal = new ArrayList<>();
        while (!header.isEmpty()) {
            int semicolon = header.indexOf(';');
            if (semicolon < 0)
                semicolon = header.length();
            if (semicolon == 0)
                break;
            String token = header.substring(0, semicolon);
            if (semicolon < header.length())
                header = header.substring(semicolon + 1);
            else
                header = "";
            try {
                int equals = token.indexOf('=');
                if (equals > 0) {
                    String name = token.substring(0, equals).trim();
                    String value = token.substring(equals+1).trim();
                    cookieal.add(new Cookie(name, value));
                }
            } catch (Throwable e) {
                log.error("error: ", e);
            }
        }
        return cookieal.toArray (new Cookie [cookieal.size()]);
    }

    private void parseRequestLine() {
        int question = requestLine.indexOf("?");
        if (question >= 0) {
            queryString = new String(requestLine.uri, question + 1, requestLine.uriEnd - question - 1);
            uri = new String(requestLine.uri, 0, question);
            // 处理参数串中带有jsessionid的情况
            int semicolon = uri.indexOf(DefaultHeaders.JSESSIONID_NAME);
            if (semicolon >= 0) {
                sessionId = uri.substring(semicolon+DefaultHeaders.JSESSIONID_NAME.length());
                uri = uri.substring(0, semicolon);
            }
        } else {
            queryString = null;
            uri = new String(requestLine.uri, 0, requestLine.uriEnd);
            int semicolon = uri.indexOf(DefaultHeaders.JSESSIONID_NAME);
            if (semicolon >= 0) {
                sessionId = uri.substring(semicolon+DefaultHeaders.JSESSIONID_NAME.length());
                uri = uri.substring(0, semicolon);
            }
        }
    }

    protected void parseParameters() {
        // 设置字符集
        String encoding = getCharacterEncoding();
        if (encoding == null) {
            encoding = "ISO-8859-1";
        }
        // 获取查询串
        String qString = getQueryString();
        if (qString != null) {
            byte[] bytes;
            try {
                bytes = qString.getBytes(encoding);
                parseParameters(this.parameters, bytes, encoding);
            } catch (UnsupportedEncodingException e) {
                log.error("error: ", e);
            }
        }
        // 获取 content Type
        String contentType = getContentType();
        if (contentType == null)
            contentType = "";
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) {
            contentType = contentType.substring(0, semicolon).trim();
        } else {
            contentType = contentType.trim();
        }
        // 对POST方法，从body中解析参数
        if ("POST".equals(getMethod()) && (getContentLength() > 0)
                && "application/x-www-form-urlencoded".equals(contentType)) {
            try {
                int max = getContentLength();
                int len = 0;
                byte[] buf = new byte[getContentLength()];
                ServletInputStream is = getInputStream();
                while (len < max) {
                    int next = is.read(buf, len, max - len);
                    if (next < 0) {
                        break;
                    }
                    len += next;
                }
                is.close();
                if (len < max) {
                    throw new RuntimeException("Content length mismatch");
                }
                parseParameters(this.parameters, buf, encoding);
            } catch (UnsupportedEncodingException ue) {
            } catch (IOException e) {
                throw new RuntimeException("Content read fail");
            }
        }
    }

    //十六进制字符到数字的转换
    private byte convertHexDigit(byte b) {
        if ((b >= '0') && (b <= '9')) return (byte) (b - '0');
        if ((b >= 'a') && (b <= 'f')) return (byte) (b - 'a' + 10);
        if ((b >= 'A') && (b <= 'F')) return (byte) (b - 'A' + 10);
        return 0;
    }

    public void parseParameters(Map<String, String[]> map, byte[] data, String encoding) throws UnsupportedEncodingException {
        if (parsed)
            return;
        if (data != null && data.length > 0) {
            int pos = 0;
            int ix = 0;
            int ox = 0;
            String key = null;
            String value;
            //解析参数串，处理特殊字符
            while (ix < data.length) {
                byte c = data[ix++];
                switch ((char) c) {
                    case '&':   //两个参数之间的分隔符，遇到这个字符保存已经解析的key和value
                        value = new String(data, 0, ox, encoding);
                        if (key != null) {
                            putMapEntry(map, key, value);
                            key = null;
                        }
                        ox = 0;
                        break;
                    case '=': //参数的key/value的分隔符
                        key = new String(data, 0, ox, encoding);
                        ox = 0;
                        break;
                    case '+': //特殊字符，空格
                        data[ox++] = (byte) ' ';
                        break;
                    case '%': //处理%NN表示的ASCII字符
                        data[ox++] = (byte) ((convertHexDigit(data[ix++]) << 4)
                                + convertHexDigit(data[ix++]));
                        break;
                    default:
                        data[ox++] = c;
                }
            }
            //最后一个参数没有&结尾
            //The last value does not end in '&'.  So save it now.
            if (key != null) {
                value = new String(data, 0, ox, encoding);
                putMapEntry(map, key, value);
            }
        }
        parsed = true;
    }

    // 给key设置新值，多值用数组来存储
    private static void putMapEntry(Map<String, String[]> map, String name, String value) {
        String[] newValues;
        String[] oldValues = map.get(name);
        if (oldValues == null) {
            newValues = new String[1];
            newValues[0] = value;
        } else {
            newValues = new String[oldValues.length + 1];
            System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
            newValues[oldValues.length] = value;
        }
        map.put(name, newValues);
    }

    public String getUri() {
        return this.uri;
    }

    @Override
    public String getAuthType() {
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        return this.cookies;
    }

    @Override
    public long getDateHeader(String s) {
        return 0;
    }

    @Override
    public String getHeader(String s) {
        return null;
    }

    @Override
    public Enumeration<String> getHeaders(String s) {
        return null;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return null;
    }

    @Override
    public int getIntHeader(String s) {
        return 0;
    }

    @Override
    public String getMethod() {
        return new String(this.requestLine.method, 0, this.requestLine.methodEnd);
    }

    @Override
    public String getPathInfo() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getContextPath() {
        return null;
    }

    @Override
    public String getQueryString() {
        return null;
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public boolean isUserInRole(String s) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String getRequestURI() {
        return null;
    }

    @Override
    public StringBuffer getRequestURL() {
        return null;
    }

    @Override
    public String getServletPath() {
        return null;
    }

    // 如果有存在的session，直接返回，如果没有，创建一个新的session
    @Override
    public HttpSession getSession(boolean create) {
        if (sessionFacade != null)
            return sessionFacade;
        if (sessionId != null) {
            session = HttpConnector.sessions.get(sessionId);
            if (session != null) {
                sessionFacade = new SessionFacade(session);
                return sessionFacade;
            } else {
                session = HttpConnector.createSession();
                sessionFacade = new SessionFacade(session);
                return sessionFacade;
            }
        } else {
            session = HttpConnector.createSession();
            sessionFacade = new SessionFacade(session);
            sessionId = session.getId();
            return sessionFacade;
        }
    }

    public String getSessionId() {
        return this.sessionId;
    }

    @Override
    public HttpSession getSession() {
        return null;
    }

    @Override
    public String changeSessionId() {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException {
        return false;
    }

    @Override
    public void login(String s, String s1) throws ServletException {

    }

    @Override
    public void logout() throws ServletException {

    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return null;
    }

    @Override
    public Part getPart(String s) throws IOException, ServletException {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) throws IOException, ServletException {
        return null;
    }

    @Override
    public Object getAttribute(String s) {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return null;
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public void setCharacterEncoding(String s) throws UnsupportedEncodingException {

    }

    @Override
    public int getContentLength() {
        return 0;
    }

    @Override
    public long getContentLengthLong() {
        return 0;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return null;
    }

    @Override
    public String getParameter(String s) {
        parseParameters();
        String[] values = parameters.get(s);
        if (values != null) {
            return values[0];
        }else {
            return null;
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        parseParameters();
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String s) {
        parseParameters();
        return parameters.get(s);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        parseParameters();
        return this.parameters;
    }

    @Override
    public String getProtocol() {
        return null;
    }

    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public String getServerName() {
        return null;
    }

    @Override
    public int getServerPort() {
        return 0;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return null;
    }

    @Override
    public String getRemoteAddr() {
        return null;
    }

    @Override
    public String getRemoteHost() {
        return null;
    }

    @Override
    public void setAttribute(String s, Object o) {

    }

    @Override
    public void removeAttribute(String s) {

    }

    @Override
    public Locale getLocale() {
        return null;
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return null;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        return null;
    }

    @Override
    public String getRealPath(String s) {
        return null;
    }

    @Override
    public int getRemotePort() {
        return 0;
    }

    @Override
    public String getLocalName() {
        return null;
    }

    @Override
    public String getLocalAddr() {
        return null;
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return null;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        return null;
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        return null;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return null;
    }
}
