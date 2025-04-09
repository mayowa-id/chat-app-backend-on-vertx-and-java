package org.example.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

public class RoomService {
    private final MongoClient mongoClient;

    public RoomService(Vertx vertx) {
        JsonObject config = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "chatapp");

        this.mongoClient = MongoClient.createShared(vertx, config);
    }

    public void createRoom(JsonObject room, Handler<AsyncResult<String>> resultHandler) {
        room.put("createdAt", System.currentTimeMillis());
        mongoClient.insert("rooms", room, resultHandler);
    }

    public void getAllRooms(Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        mongoClient.find("rooms", new JsonObject(), resultHandler);
    }

    public void deleteRoom(String roomId, Handler<AsyncResult<Void>> resultHandler) {
        JsonObject query = new JsonObject().put("_id", roomId);
        mongoClient.removeDocument("rooms", query, res -> resultHandler.handle(res.mapEmpty()));
    }
}

