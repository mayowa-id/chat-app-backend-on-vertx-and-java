package org.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.core.json.JsonObject;
import org.example.verticles.HttpServerVerticle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject config = config().getJsonObject("config");
        String jwtSecret = config.getJsonObject("jwt").getString("secret");

        JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(jwtSecret)));

        Map<String, Set<ServerWebSocket>> roomConnections = new ConcurrentHashMap<>();
        HttpServerVerticle httpServerVerticle = new HttpServerVerticle(roomConnections, jwtAuth);

        vertx.deployVerticle(httpServerVerticle)
                .onSuccess(id -> {
                    System.out.println("HttpServerVerticle deployed successfully");
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
//        Map<String, Set<ServerWebSocket>> roomConnections = new ConcurrentHashMap<>();
        vertx.deployVerticle(new HttpServerVerticle());
    }

}
