package org.example.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingAlgorithm;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.auth.VertxContextPRNG;
import org.mindrot.jbcrypt.BCrypt;


public class UserService {
    private final MongoClient mongoClient;
    private final HashingAlgorithm bcrypt = (HashingAlgorithm) new BCrypt();

    public UserService(Vertx vertx) {
        this.mongoClient = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "chat-app"));
    }

    public void registerUser(JsonObject user, Handler<AsyncResult<String>> resultHandler) {
        String password = user.getString("password");
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());

        user.put("password", hashed);
        user.put("createdAt", System.currentTimeMillis());

        mongoClient.save("users", user, resultHandler);
    }

    public void authenticateUser(String email, String password, Handler<AsyncResult<JsonObject>> resultHandler) {
        JsonObject query = new JsonObject().put("email", email);

        mongoClient.findOne("users", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                JsonObject user = res.result();
                boolean match = BCrypt.checkpw(password, user.getString("password"));

                if (match) {
                    user.remove("password");
                    resultHandler.handle(io.vertx.core.Future.succeededFuture(user));
                } else {
                    resultHandler.handle(io.vertx.core.Future.failedFuture("Invalid credentials"));
                }
            } else {
                resultHandler.handle(io.vertx.core.Future.failedFuture("User not found"));
            }
        });
    }
}
