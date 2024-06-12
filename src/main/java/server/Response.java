package server;

import java.io.OutputStream;

/**
 * @author kbws
 * @date 2024/6/12
 * @description: 请求返回对象
 */
public class Response {

    private Request request;

    private OutputStream output;

    public Response(OutputStream output) {
        this.output = output;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public OutputStream getOutput() {
        return this.output;
    }
}
