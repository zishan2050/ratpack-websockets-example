package com.github.gregwhitaker.ratpackwebsockets.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.websocket.WebSocket;
import ratpack.websocket.WebSocketClose;
import ratpack.websocket.WebSocketHandler;
import ratpack.websocket.WebSocketMessage;
import ratpack.websocket.WebSockets;
import rx.RxReactiveStreams;
import rx.subjects.PublishSubject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChatHandler implements Handler {
    private static final Logger LOG = LoggerFactory.getLogger(ChatHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> clients = new HashSet<>();
    private final PublishSubject<String> events = PublishSubject.create();

    @Override
    public void handle(Context ctx) throws Exception {
        WebSockets.websocket(ctx, new WebSocketHandler<String>() {

            @Override
            public String onOpen(WebSocket webSocket) throws Exception {
                String client = ctx.getRequest().getQueryParams().get("client");

                LOG.info("Websocket opened for client: {}", client);

                if (client == null || client.isEmpty()) {
                    webSocket.close(500, "Client id is required");
                } else if (clients.contains(client)) {
                    webSocket.close(500, "Client is already connected");
                } else {
                    Map<String, Object> response = new HashMap<>();
                    response.put("type", "clientconnect");
                    response.put("client", client);
                    response.put("success", true);
                    response.put("connectedClients", Collections.unmodifiableSet(clients));

                    webSocket.send(mapper.writer().writeValueAsString(response));

                    clients.add(client);
                }

                return null;
            }

            @Override
            public void onClose(WebSocketClose<String> close) throws Exception {
                String client = ctx.getRequest().getQueryParams().get("client");
                clients.remove(client);
            }

            @Override
            public void onMessage(WebSocketMessage<String> frame) throws Exception {
                events.onNext(frame.getText());
            }
        });

        WebSockets.websocketBroadcast(ctx, RxReactiveStreams.toPublisher(events));
    }
}