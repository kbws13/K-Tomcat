package server;

import java.io.IOException;

/**
 * @author kbws
 * @date 2024/6/12
 * @description: 自定义 Servlet 接口
 */
public interface Servlet {

    void service(Request req, Response res) throws IOException;

}
