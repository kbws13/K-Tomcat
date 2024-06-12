package server;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author kbws
 * @date 2024/6/12
 * @description: 负责调用 Servlet
 */
public class HttpProcessor {
    public HttpProcessor() {
    }

    public void process(Socket socket) {
        InputStream input = null;
        OutputStream output = null;

        try {
            input = socket.getInputStream();
            output = socket.getOutputStream();
            // 创建一个请求对象，并且处理
            Request request = new Request(input);
            request.parse();
            // 创建一个响应对象
            Response response = new Response(output);
            response.setRequest(request);
            // 检查这是否是一个 Servlet 请求或者静态资源请求，一个 Servlet 请求应该以`/servlet/`开头
            if (request.getUri().startsWith("/servlet/")) {
                ServletProcessor processor = new ServletProcessor();
                processor.process(request, response);
            } else {
                StaticResourceProcessor processor = new StaticResourceProcessor();
                processor.process(request, response);
            }
            // 关闭连接
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
