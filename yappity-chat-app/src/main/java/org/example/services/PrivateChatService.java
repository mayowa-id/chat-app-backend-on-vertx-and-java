package org.example.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

public class PrivateChatService {
    private final MongoClient mongoClient;

    public PrivateChatService(Vertx vertx) {
        JsonObject config = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "chat_app");

        this.mongoClient = MongoClient.createShared(vertx, config);
    }

    public void savePrivateMessage(JsonObject message, Handler<AsyncResult<String>> resultHandler) {
        message.put("timestamp", System.currentTimeMillis());
        mongoClient.insert("private_messages", message, resultHandler);
    }

    public void getPrivateMessages(String user1, String user2, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        JsonObject query = new JsonObject().put("$or", List.of(
                new JsonObject().put("from", user1).put("to", user2),
                new JsonObject().put("from", user2).put("to", user1)
        ));

        FindOptions options = new FindOptions()
                .setSort(new JsonObject().put("timestamp", 1)); // Chronological order

        mongoClient.findWithOptions("private_messages", query, options, resultHandler);
    }
}

