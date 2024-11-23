package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author kbws
 * @date 2024/6/12
 * @description: 负责调用 Servlet
 */
public class HttpProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HttpProcessor.class);

    Socket socket;

    boolean available = false;

    HttpConnector httpConnector;

    public HttpProcessor(HttpConnector httpConnector) {
        this.httpConnector = httpConnector;
    }

    public void process(Socket socket) {
        InputStream input;
        OutputStream output;
        try {
            input = socket.getInputStream();
            output = socket.getOutputStream();
            // 创建请求对象并解析
            HttpRequest request = new HttpRequest(input);
            request.parse(socket);

            // handle session
            if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
                request.getSession(true);
            }
            // 创建响应对象
            HttpResponse response = new HttpResponse(output);
            response.setRequest(request);
            // response.sendStaticResource();
            // 检查这是对servlet还是静态资源的请求
            // a request for a servlet begins with "/servlet/"
            if (request.getUri().startsWith("/servlet/")) {
                ServletProcessor processor = new ServletProcessor();
                processor.process(request, response);
            } else {
                StaticResourceProcessor processor = new StaticResourceProcessor();
                processor.process(request, response);
            }
            // 关闭 socket
            socket.close();
        } catch (Exception e) {
            log.error("error: ", e);
        }
    }

    synchronized void assign(Socket socket) {
        // 等待 connector 提供一个新的 socket
        while (available) {
            try {
                wait();
            } catch (InterruptedException e) {
                log.error("error: ", e);
            }
        }
        // 获取到新的 socket
        this.socket = socket;
        // 把标志设置回去
        available = true;
        // 通知另外的线程
        notifyAll();
    }

    private synchronized Socket await() {
        // 等待 connector 提供一个新的 socket
        while (!available) {
            try {
                wait();
            } catch (InterruptedException e) {
                log.error("error: ", e);
            }
        }
        // 获得这个新的Socket
        Socket socket = this.socket;
        //设置标志为false
        available = false;
        //通知另外的线程
        notifyAll();
        return (socket);
    }

    @Override
    public void run() {
        while (true) {
            // 等待 Socket 分配过来
            Socket socket = await();
            if (socket == null) {
                continue;
            }
            // 处理请求
            process(socket);
            // 回收 processor
            httpConnector.recycle(this);
        }
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }
}
