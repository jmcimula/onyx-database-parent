package com.onyx.server;

import com.onyx.endpoint.ServerEndpoint;
import com.onyx.request.pojo.*;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by timothy.osborn on 4/22/15.
 */
public class DefaultMessageListener extends AbstractMessageListener {

    protected ServerEndpoint defaultEndpoint = null;

    /**
     * Constructor
     *
     */
    public DefaultMessageListener(ServerEndpoint defaultEndpoint)
    {
        this.defaultEndpoint = defaultEndpoint;
    }

    /**
     * Handle token.  Invoke on default endpoint
     *
     * @param token
     */
    @Override
    void handleToken(RequestToken token, WebSocketChannel channel, WebSocketCallback<Void> callback) {

        Runnable thread = () -> {
            try
            {
                defaultEndpoint.handleToken(token, channel, callback);
            }
            // Error Handling set payload to exception
            catch (Exception e)
            {
                token.setPayload(e);
            }

        };

        channel.getWorker().submit(thread);
    }
}
