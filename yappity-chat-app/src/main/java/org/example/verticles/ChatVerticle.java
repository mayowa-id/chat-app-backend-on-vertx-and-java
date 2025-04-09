package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.example.services.ChatService;
import io.vertx.core.http.ServerWebSocket;

import java.util.Collections;
import java.util.Set;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatVerticle extends AbstractVerticle {

    private final Map<String, Set<ServerWebSocket>> roomConnections;

    public ChatVerticle(Map<String, Set<ServerWebSocket>> roomConnections) {
        this.roomConnections = roomConnections;
    }

    @Override
    public void start() {
        vertx.eventBus().consumer(ChatService.CHAT_ADDRESS, message -> {
            JsonObject msg = (JsonObject) message.body();
            String room = msg.getString("room");

            Set<ServerWebSocket> sockets = roomConnections.getOrDefault(room, Collections.emptySet());

            for (ServerWebSocket socket : sockets) {
                if (!socket.isClosed()) {
                    socket.writeTextMessage(msg.encode());
                }
            }
        });
    }
}
