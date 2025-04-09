package org.example.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import org.example.models.Message;

import java.util.List;


public class ChatService {
        private final Vertx vertx;
        private final MongoClient mongoClient;
        public static final String CHAT_ADDRESS = "chat.message";

    public ChatService(Vertx vertx) {
            this.vertx = vertx;
            JsonObject config = new JsonObject()
                    .put("connection_string", "mongodb://localhost:27017")
                    .put("db_name", "chat_app");

            this.mongoClient = MongoClient.createShared(vertx, config);
        }

        public void saveMessage(Message message) {
            JsonObject doc = JsonObject.mapFrom(message);
            mongoClient.insert("messages", doc, res -> {
                if (res.succeeded()) {
                    System.out.println("Message saved to MongoDB: " + res.result());
                } else {
                    System.err.println("Failed to save message: " + res.cause().getMessage());
                }
            });
        }

        public void broadcastMessage(Message message) {
            saveMessage(message);

        }
    public void getMessagesByRoom(String room, int limit, Long beforeTimestamp, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        JsonObject query = new JsonObject().put("room", room);

        if (beforeTimestamp != null) {
            query.put("timestamp", new JsonObject().put("$lt", beforeTimestamp));
        }

        JsonObject sort = new JsonObject().put("timestamp", -1); // Newest first

        FindOptions options = new FindOptions()
                .setSort(sort)
                .setLimit(limit);

        mongoClient.findWithOptions("messages", query, options, resultHandler);
    }

}

