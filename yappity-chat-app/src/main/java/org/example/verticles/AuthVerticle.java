package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import org.example.models.User;

import java.time.Instant;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthVerticle.class);
    private MongoClient mongoClient;
    private Redis redisClient;
    private JWTAuth jwtAuth;
    public JWTAuth getJwtAuth() {
        return jwtAuth;
    }


    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("Starting AuthVerticle");
        JsonObject config = config().getJsonObject("config");
        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", config.getJsonObject("mongodb").getString("connection_string"))
                .put("db_name", config.getJsonObject("mongodb").getString("db_name"));

        mongoClient = MongoClient.createShared(vertx, mongoConfig);

        RedisOptions redisOptions = new RedisOptions()
                .setConnectionString("redis://" + config.getJsonObject("redis").getString("host") + ":" +
                        config.getJsonObject("redis").getInteger("port"));

        Redis.createClient(vertx, redisOptions)
                .connect()
                .onSuccess(conn -> {
                    LOGGER.info("Redis connection established in AuthVerticle");
                    setupJwtAuth(config);
                    setupEventBusConsumers();
                    startPromise.complete();
                })
                .onFailure(err -> {
                    LOGGER.error("Redis connection failed in AuthVerticle", err);
                    startPromise.fail(err);
                });
    }

    private void setupJwtAuth(JsonObject config) {
        JWTAuthOptions jwtOptions = new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(config.getJsonObject("jwt").getString("secret")));

        jwtAuth = JWTAuth.create(vertx, jwtOptions);
        LOGGER.info("JWT Auth provider created");
    }

    private void setupEventBusConsumers() {
        vertx.eventBus().consumer("auth.register", message -> {
            JsonObject user = (JsonObject) message.body();
            String username = user.getString("username");
            String password = user.getString("password");
            String email = user.getString("email");

            JsonObject query = new JsonObject().put("username", username);
            mongoClient.findOne("users", query, null)
                    .onSuccess(existing -> {
                        if (existing != null) {
                            message.reply(new JsonObject().put("success", false).put("message", "Username already exists"));
                            return;
                        }

                        try {
                            String passwordHash = hashPassword(password);
                            User newUser = new User(username, passwordHash, email);

                            mongoClient.insert("users", newUser.toJson())
                                    .onSuccess(id -> {
                                        LOGGER.info("User registered: {}", username);
                                        message.reply(new JsonObject()
                                                .put("success", true)
                                                .put("message", "User registered successfully"));
                                    })
                                    .onFailure(err -> {
                                        LOGGER.error("Failed to save user", err);
                                        message.reply(new JsonObject()
                                                .put("success", false)
                                                .put("message", "Failed to register user"));
                                    });

                        } catch (Exception e) {
                            LOGGER.error("Error during registration", e);
                            message.reply(new JsonObject()
                                    .put("success", false)
                                    .put("message", "Registration error"));
                        }
                    })
                    .onFailure(err -> {
                        LOGGER.error("Database error during registration", err);
                        message.reply(new JsonObject()
                                .put("success", false)
                                .put("message", "Registration error"));
                    });
        });

        vertx.eventBus().consumer("auth.login", message -> {
            JsonObject credentials = (JsonObject) message.body();
            String username = credentials.getString("username");
            String password = credentials.getString("password");

            JsonObject query = new JsonObject().put("username", username);
            mongoClient.findOne("users", query, null)
                    .onSuccess(user -> {
                        if (user == null) {
                            message.reply(new JsonObject()
                                    .put("success", false)
                                    .put("message", "Invalid username or password"));
                            return;
                        }
                        try {
                            String storedHash = user.getString("passwordHash");
                            String inputHash = hashPassword(password);

                            if (!storedHash.equals(inputHash)) {
                                message.reply(new JsonObject()
                                        .put("success", false)
                                        .put("message", "Invalid username or password"));
                                return;
                            }
                            JsonObject update = new JsonObject()
                                    .put("$set", new JsonObject()
                                            .put("lastSeen", Instant.now().toString())
                                            .put("online", true));

                            mongoClient.updateCollection("users", query, update)
                                    .onSuccess(v -> {
                                        JsonObject jwtConfig = config().getJsonObject("config").getJsonObject("jwt");
                                        JsonObject jwtClaims = new JsonObject()
                                                .put("sub", user.getString("_id"))
                                                .put("username", username)
                                                .put("email", user.getString("email"))
                                                .put("iss", jwtConfig.getString("issuer"))
                                                .put("iat", Instant.now().getEpochSecond())
                                                .put("exp", Instant.now().plusSeconds(jwtConfig.getInteger("expires_in_minutes") * 60).getEpochSecond());

                                        String token = jwtAuth.generateToken(jwtClaims);

                                        LOGGER.info("User logged in: {}", username);
                                        message.reply(new JsonObject()
                                                .put("success", true)
                                                .put("message", "Login successful")
                                                .put("token", token)
                                                .put("userId", user.getString("_id")));
                                    })
                                    .onFailure(err -> {
                                        LOGGER.error("Failed to update user status", err);
                                        message.reply(new JsonObject()
                                                .put("success", false)
                                                .put("message", "Login error"));
                                    });

                        } catch (Exception e) {
                            LOGGER.error("Error during login", e);
                            message.reply(new JsonObject()
                                    .put("success", false)
                                    .put("message", "Login error"));
                        }
                    })
                    .onFailure(err -> {
                        LOGGER.error("Database error during login", err);
                        message.reply(new JsonObject()
                                .put("success", false)
                                .put("message", "Login error"));
                    });
        });

        vertx.eventBus().consumer("auth.verify", message -> {
            String token = (String) message.body();

            try {
                jwtAuth.authenticate(new JsonObject().put("token", token))
                        .onSuccess(user -> {
                            message.reply(new JsonObject()
                                    .put("success", true)
                                    .put("userId", user.principal().getString("sub"))
                                    .put("username", user.principal().getString("username")));
                        })
                        .onFailure(err -> {
                            LOGGER.warn("Invalid token", err);
                            message.reply(new JsonObject()
                                    .put("success", false)
                                    .put("message", "Invalid or expired token"));
                        });
            } catch (Exception e) {
                LOGGER.error("Token verification error", e);
                message.reply(new JsonObject()
                        .put("success", false)
                        .put("message", "Token validation error"));
            }
        });
        vertx.eventBus().consumer("auth.logout", message -> {
            String userId = (String) message.body();

            JsonObject query = new JsonObject().put("_id", userId);
            JsonObject update = new JsonObject()
                    .put("$set", new JsonObject()
                            .put("lastSeen", Instant.now().toString())
                            .put("online", false));

            mongoClient.updateCollection("users", query, update)
                    .onSuccess(v -> {
                        LOGGER.info("User logged out: {}", userId);
                        message.reply(new JsonObject()
                                .put("success", true)
                                .put("message", "Logout successful"));
                    })
                    .onFailure(err -> {
                        LOGGER.error("Failed to update user status on logout", err);
                        message.reply(new JsonObject()
                                .put("success", false)
                                .put("message", "Logout error"));
                    });
        });
    }
    private String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hashBytes);
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        LOGGER.info("Stopping AuthVerticle");

        if (mongoClient != null) {
            mongoClient.close();
        }
        if (redisClient != null) {
            redisClient.close();
        }
        stopPromise.complete();
    }
}