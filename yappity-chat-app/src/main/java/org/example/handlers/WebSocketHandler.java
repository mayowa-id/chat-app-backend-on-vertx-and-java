package org.example.handlers;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.example.models.Message;
import org.example.services.ChatService;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketHandler {

    private final JWTAuth jwtAuth;
    private final Map<String, Set<ServerWebSocket>> roomConnections;
    private final Map<String, ServerWebSocket> userConnections = new ConcurrentHashMap<>();
    private final ChatService chatService;

    public WebSocketHandler(JWTAuth jwtAuth, Map<String, Set<ServerWebSocket>> roomConnections, ChatService chatService) {
        this.jwtAuth = jwtAuth;
        this.roomConnections = roomConnections;
        this.chatService = chatService;
    }

    public void handle(ServerWebSocket webSocket) {
        if (!webSocket.path().equals("/ws/chat")) {
            webSocket.reject();
            return;
        }

        String token = getTokenFromQuery(webSocket.query());
        if (token == null) {
            webSocket.reject();
            return;
        }

        jwtAuth.authenticate(new JsonObject().put("token", token))
                .onSuccess(user -> {
                    String username = user.principal().getString("username");
                    userConnections.put(username, webSocket);

                    webSocket.accept();

                    webSocket.textMessageHandler(message -> {
                        JsonObject json = new JsonObject(message);
                        String type = json.getString("type", "message");

                        switch (type) {
                            case "read_receipt":
                                handleReadReceipt(json);
                                break;

                            case "typing":
                                handleTyping(json);
                                break;

                            default:
                                System.out.println("Unknown message type: " + type);
                        }
                    });

                    webSocket.closeHandler(v -> {
                        userConnections.remove(username);
                        roomConnections.values().forEach(sockets -> sockets.remove(webSocket));
                    });
                })
                .onFailure(err -> {
                    webSocket.reject();
                });
    }

    private String getTokenFromQuery(String query) {
        if (query == null || !query.contains("token=")) return null;
        try {
            return query.split("token=")[1].split("&")[0];
        } catch (Exception e) {
            return null;
        }
    }

    private void handleChatMessage(JsonObject json, String sender) {
        String room = json.getString("room");
        String recipient = json.getString("to");
        String content = json.getString("content");

        Message message = new Message(sender, content, room);
        chatService.saveMessage(message); // async insert to Mongo

        JsonObject messageJson = JsonObject.mapFrom(message)
                .put("type", "message")
                .put("to", recipient);

        // Broadcast to room
        Set<ServerWebSocket> sockets = roomConnections.computeIfAbsent(room, r -> ConcurrentHashMap.newKeySet());
        for (ServerWebSocket socket : sockets) {
            if (!socket.isClosed()) {
                socket.writeTextMessage(messageJson.encode());
            }
        }

        // Send delivery receipt to sender
        ServerWebSocket senderSocket = userConnections.get(sender);
        if (senderSocket != null && !senderSocket.isClosed()) {
            JsonObject receipt = new JsonObject()
                    .put("type", "receipt")
                    .put("messageId", message.getId())
                    .put("status", "delivered")
                    .put("timestamp", System.currentTimeMillis());

            senderSocket.writeTextMessage(receipt.encode());
        }
    }

    private void forwardTypingStatus(JsonObject json) {
        String to = json.getString("to");
        ServerWebSocket recipient = userConnections.get(to);
        if (recipient != null && !recipient.isClosed()) {
            recipient.writeTextMessage(json.encode());
        }
    }

    private void handleReadReceipt(JsonObject json) {
        String room = json.getString("room");
        String messageId = json.getString("messageId");
        String reader = json.getString("reader");

        if (room == null || messageId == null || reader == null) return;

        JsonObject receipt = new JsonObject()
                .put("type", "read_receipt")
                .put("messageId", messageId)
                .put("reader", reader);

        Set<ServerWebSocket> sockets = roomConnections.getOrDefault(room, Collections.emptySet());

        for (ServerWebSocket socket : sockets) {
            if (!socket.isClosed()) {
                socket.writeTextMessage(receipt.encode());
            }
        }
    }

    private void handleTyping(JsonObject json) {
        String room = json.getString("room");
        String sender = json.getString("sender");
        boolean isTyping = json.getBoolean("isTyping", false);

        if (room == null || sender == null) return;

        JsonObject typingStatus = new JsonObject()
                .put("type", "typing")
                .put("sender", sender)
                .put("isTyping", isTyping);

        Set<ServerWebSocket> sockets = roomConnections.getOrDefault(room, Collections.emptySet());

        for (ServerWebSocket socket : sockets) {
            if (!socket.isClosed()) {
                socket.writeTextMessage(typingStatus.encode());
            }
        }
    }


}
