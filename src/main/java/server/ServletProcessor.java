package server;

import org.apache.commons.lang3.text.StrSubstitutor;

import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandler;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author kbws
 * @date 2024/6/12
 * @description:
 */
public class ServletProcessor {

    // 响应头定义，里面包含变量
    private static String OKMessage = "HTTP/1.1 ${StatusCode} ${StatusName}\r\n" +
            "Content-Type: ${ContentType}\r\n" +
            "Server: minit\r\n" +
            "Date: ${ZonedDateTime}\r\n" +
            "\r\n";

    public void process(Request request, Response response) {
        // 获取 URI
        String uri = request.getUri();
        // 首先根据uri最后一个/号来定位，后面的字符串认为是servlet名字
        String servletName = uri.substring(uri.lastIndexOf("/") + 1);
        URLClassLoader loader = null;
        PrintWriter printWriter = null;
        try {
            // 创建一个 URLClassLoader
            URL[] urls = new URL[1];
            URLStreamHandler streamHandler = null;
            // 从全局变量 HttpServer.WEB_ROOT 中设置类的目录
            File classPath = new File(HttpServer.WEB_ROOT);
            String repository = (new URL("file", null, classPath.getCanonicalPath() + File.separator)).toString();
            urls[0] = new URL(null, repository, streamHandler);
            loader = new URLClassLoader(urls);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
        // 获取 PrintWriter
        try {
            response.setCharacterEncoding("UTF-8");
            printWriter = response.getWriter();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //由上面的URLClassLoader加载这个servlet
        Class<?> servletClass = null;
        try {
            servletClass = loader.loadClass(servletName);
        } catch (ClassNotFoundException e) {
            System.out.println(e.toString());
        }
        // 生成响应头
        String head = composeResponseHead();
        printWriter.println(head);
        // 创建servlet新实例，然后调用service()，由它来写动态内容到响应体
        Servlet servlet = null;
        try {
            // 调用 servlet，由 servlet 写 response 体
            servlet = (Servlet) servletClass.newInstance();
            servlet.service(request, response);
        } catch (Exception e1) {
            System.out.println(e1);
        }
    }

    // 生成响应头，根据协议格式替换变量
    private String composeResponseHead() {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("StatusCode", "200");
        valuesMap.put("StatusName", "OK");
        valuesMap.put("ContentType", "text/html;charset=uft-8");
        valuesMap.put("ZonedDateTime", DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now()));
        StrSubstitutor sub = new StrSubstitutor(valuesMap);
        return sub.replace(OKMessage);
    }
}
