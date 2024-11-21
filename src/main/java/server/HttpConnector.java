package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * @author kbws
 * @date 2024/6/12
 * @description: 负责处理接收、响应客户端请求
 */
public class HttpConnector implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HttpConnector.class);

    int minProcessors = 3;
    int maxProcessors = 10;
    int curProcessors = 0;

    // 存放多个 processors 的池
    Deque<HttpProcessor> processors = new ArrayDeque<>();

    @Override
    public void run() {
        ServerSocket serverSocket = null;
        int port = 8080;
        try {
            serverSocket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            log.error("error: ", e);
            System.exit(1);
        }
        // 初始化 processors 池
        for (int i = 0; i < minProcessors; i++) {
            HttpProcessor httpProcessor = new HttpProcessor(this);
            processors.push(httpProcessor);
        }
        curProcessors = minProcessors;
        while (true) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                // 从池中获取一个 processor（池中有可能新建）
                HttpProcessor processor = createProcessor();
                if (processor == null) {
                    socket.close();
                }
                processor.assign(socket);
                // 关闭 socket 连接
                socket.close();
            } catch (Exception e) {
                log.error("error: ", e);
            }
        }
    }

    // 从池子中获取一个processor，如果池子为空且小于最大限制，则新建一个
    private HttpProcessor createProcessor() {
        synchronized (processors) {
            if (processors.size() > 0) {
                //获取一个
                return processors.pop();
            }
            if (curProcessors < maxProcessors) {
                //新建一个
                return (newProcessor());
            } else {
                return (null);
            }
        }
    }

    // 新建一个processor
    private HttpProcessor newProcessor() {
        HttpProcessor initProcessor = new HttpProcessor(this);
        processors.push(initProcessor);
        curProcessors++;
        return processors.pop();
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    void recycle(HttpProcessor processor) {
        processors.push(processor);
    }
}
