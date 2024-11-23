package server;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author kbws
 * @date 2024/6/12
 * @description: 寻找静态资源类
 */
public class StaticResourceProcessor {

    private static final int BUFFER_SIZE = 1024;
    private static final Logger log = LoggerFactory.getLogger(StaticResourceProcessor.class);

    // 下面的字符串是当文件没有找到时返回的404错误描述
    private static String fileNotFoundMessage = "HTTP/1.1 404 File Not Found\r\n" +
            "Content-Type: text/html\r\n" +
            "Content-Length: 23\r\n" +
            "\r\n" +
            "<h1>File Not Found</h1>";

    // 下面的字符串是正常情况下返回的，根据http协议，里面包含了相应的变量。
    private static String OKMessage = "HTTP/1.1 ${StatusCode} ${StatusName}\r\n" +
            "Content-Type: ${ContentType}\r\n" +
            "Content-Length: ${ContentLength}\r\n" +
            "Server: minit\r\n" +
            "Date: ${ZonedDateTime}\r\n" +
            "\r\n";

    // 处理过程很简单，先将响应头写入输出流，然后从文件中读取内容写入输出流
    public void process(HttpRequest request, HttpResponse response) throws IOException {
        byte[] bytes = new byte[BUFFER_SIZE];
        FileInputStream fis = null;
        OutputStream output;
        try {
            output = response.getOutput();
            File file = new File(HttpServer.WEB_ROOT, request.getUri());
            if (file.exists()) {
                // 拼接响应头
                String head = composeResponseHead(file);
                output.write(head.getBytes(StandardCharsets.UTF_8));
                //读取文件内容，写入输出流
                fis = new FileInputStream(file);
                int ch = fis.read(bytes, 0, BUFFER_SIZE);
                while (ch != -1) {
                    output.write(bytes, 0, ch);
                    ch = fis.read(bytes, 0, BUFFER_SIZE);
                }
                output.flush();
            } else {
                output.write(fileNotFoundMessage.getBytes());
            }
        } catch (IOException e) {
            log.error("e: ", e);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    // 拼接响应头，填充变量值
    private String composeResponseHead(File file) {
        long fileLength = file.length();
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("StatusCode", "200");
        valuesMap.put("StatusName", "OK");
        valuesMap.put("ContentType", "text/html;charset=utf-8");
        valuesMap.put("ContentLength", fileLength);
        valuesMap.put("ZonedDateTime", DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now()));
        StrSubstitutor sub = new StrSubstitutor(valuesMap);
        String responseHead = sub.replace(OKMessage);
        return responseHead;
    }

}
