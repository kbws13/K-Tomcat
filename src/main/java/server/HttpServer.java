package server;

import java.io.File;

/**
 * @author kbws
 * @date 2024/6/12
 * @description: 处理 HTTP 请求以及传输返回报文
 */
public class HttpServer {

    public static final String WEB_ROOT = System.getProperty("user.dir") + File.separator + "webroot";


    public static void main(String[] args) {
        HttpConnector connector = new HttpConnector();
        connector.start();
    }
}
