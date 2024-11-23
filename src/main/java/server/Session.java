package server;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kbws
 * @date 2024/11/22
 * @description:
 */
public class Session implements HttpSession {

    private String sessionId;
    private Long creationTime;
    private Boolean valid;
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    @Override
    public long getCreationTime() {
        return this.creationTime;
    }

    @Override
    public String getId() {
        return this.sessionId;
    }

    @Override
    public long getLastAccessedTime() {
        return 0;
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public void setMaxInactiveInterval(int i) {

    }

    @Override
    public int getMaxInactiveInterval() {
        return 0;
    }

    @Override
    public HttpSessionContext getSessionContext() {
        return null;
    }

    @Override
    public Object getAttribute(String s) {
        return this.attributes.get(s);
    }

    @Override
    public Object getValue(String s) {
        return this.attributes.get(s);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(this.attributes.keySet());
    }

    @Override
    public String[] getValueNames() {
        return new String[0];
    }

    @Override
    public void setAttribute(String s, Object o) {
        this.attributes.put(s, o);
    }

    @Override
    public void putValue(String s, Object o) {
        this.attributes.put(s, o);
    }

    @Override
    public void removeAttribute(String s) {
        this.attributes.remove(s);
    }

    @Override
    public void removeValue(String s) {

    }

    @Override
    public void invalidate() {
        this.valid = false;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public void setValid(boolean b) {
        this.valid = b;
    }

    public void setCreationTime(long currentTimeMillis) {
        this.creationTime = currentTimeMillis;
    }

    public void setId(String sessionId) {
        this.sessionId = sessionId;
    }
}
