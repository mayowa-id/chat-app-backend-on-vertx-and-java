package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import org.example.models.Message;
import org.example.services.ChatService;
import org.example.services.PrivateChatService;
import org.example.services.RoomService;
import org.example.services.UserService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.err;

public class HttpServerVerticle extends AbstractVerticle {
    public  Map<String, Set<ServerWebSocket>> roomConnections;
    public JWTAuth jwtAuth;
    private ChatService chatService;
    private MongoClient mongoClient;
    private RoomService roomService;
    private PrivateChatService privateChatService;
    private final Map<String, ServerWebSocket> userConnections = new ConcurrentHashMap<>();


    public HttpServerVerticle(){

    }
    public HttpServerVerticle(Map<String, Set<ServerWebSocket>> roomConnections, JWTAuth jwtAuth) {
        this.roomConnections = roomConnections;
        this.jwtAuth = jwtAuth;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "chat-app"));


        chatService = new ChatService(vertx);

        Router router = Router.router(vertx);
        router.route("/*").handler(StaticHandler.create("webroot"));


        roomService = new RoomService(vertx);

        privateChatService = new PrivateChatService(vertx);

        vertx.createHttpServer()
                .webSocketHandler(this::handleWebSocket)
                .requestHandler(router)
                .listen(8888)
                .onSuccess(server -> {
                    System.out.println("HTTP/WebSocket server started on port " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);

//        router.get("/api/messages/:room").handler(ctx -> {
//            String room = ctx.pathParam("room");
//
//            chatService.getMessagesByRoom(room, res -> {
//                if (res.succeeded()) {
//                    ctx.response()
//                            .putHeader("Content-Type", "application/json")
//                            .end(new JsonArray(res.result()).encode());
//                } else {
//                    ctx.response().setStatusCode(500).end("Failed to retrieve messages");
//                }
//            });
//        });


        router.get("/api/messages/:room").handler(ctx -> {
            String room = ctx.pathParam("room");

            int limit = 20; // default
            try {
                limit = Integer.parseInt(ctx.queryParam("limit").get(0));
            } catch (Exception ignored) {}

            Long beforeTimestamp = null;
            try {
                beforeTimestamp = Long.parseLong(ctx.queryParam("before").get(0));
            } catch (Exception ignored) {}

            chatService.getMessagesByRoom(room, limit, beforeTimestamp, res -> {
                if (res.succeeded()) {
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonArray(res.result()).encode());
                } else {
                    ctx.response().setStatusCode(500).end("Failed to retrieve messages" );
                }

            });
        });

        router.post("/api/register").handler(routingContext -> {
            JsonObject body = routingContext.getBodyAsJson();
            String username = body.getString("username");
            String email = body.getString("email");
            String password = body.getString("password");

            if (username == null || email == null || password == null) {
                routingContext.response().setStatusCode(400).end("Missing required fields");
                return;
            }

            // Check if username or email exists
            JsonObject query = new JsonObject().put("username", username);
            mongoClient.findOne("users", query, null).onSuccess(existingUser -> {
                if (existingUser != null) {
                    routingContext.response().setStatusCode(400).end("Username already exists");
                } else {
                    // Hash the password
                    try {
                        String hashedPassword = hashPassword(password);

                        // Save user to MongoDB
                        JsonObject newUser = new JsonObject()
                                .put("username", username)
                                .put("email", email)
                                .put("passwordHash", hashedPassword);

                        mongoClient.insert("users", newUser).onSuccess(id -> {
                            routingContext.response().setStatusCode(201).end("User registered successfully");
                        }).onFailure(err -> {
                            routingContext.response().setStatusCode(500).end("Failed to register user");
                        });
                    } catch (Exception e) {
                        routingContext.response().setStatusCode(500).end("Error hashing password");
                    }
                }
            }).onFailure(err -> {
                routingContext.response().setStatusCode(500).end("Database error");
            });
        });


        router.post("/api/login").handler(routingContext -> {
            JsonObject body = routingContext.getBodyAsJson();
            String username = body.getString("username");
            String password = body.getString("password");

            if (username == null || password == null) {
                routingContext.response().setStatusCode(400).end("Missing required fields");
                return;
            }

            JsonObject query = new JsonObject().put("username", username);
            mongoClient.findOne("users", query, null).onSuccess(user -> {
                if (user == null) {
                    routingContext.response().setStatusCode(400).end("Invalid username or password");
                } else {
                    String storedHash = user.getString("passwordHash");
                    try {
                        if (checkPassword(password, storedHash)) {
                            // Passwords match, create JWT token
                            JsonObject jwtClaims = new JsonObject()
                                    .put("sub", user.getString("_id"))
                                    .put("username", username)
                                    .put("email", user.getString("email"));

                            String token = jwtAuth.generateToken(jwtClaims);
                            routingContext.response().setStatusCode(200).end(new JsonObject().put("token", token).encode());
                        } else {
                            routingContext.response().setStatusCode(400).end("Invalid username or password");
                        }
                    } catch (Exception e) {
                        routingContext.response().setStatusCode(500).end("Error validating password");
                    }
                }
            }).onFailure(err -> {
                routingContext.response().setStatusCode(500).end("Database error");
            });
        });

        UserService userService = new UserService(vertx);

// Register
        router.post("/api/register").handler(ctx -> {
            ctx.request().bodyHandler(body -> {
                JsonObject user = body.toJsonObject();

                userService.registerUser(user, res -> {
                    if (res.succeeded()) {
                        ctx.response().setStatusCode(201).end("User registered!");
                    } else {
                        res.cause().printStackTrace();
                        ctx.response().setStatusCode(500).end("Failed to register user");
                    }
                });
            });
        });

// Login
        router.post("/api/login").handler(ctx -> {
            ctx.request().bodyHandler(body -> {
                JsonObject credentials = body.toJsonObject();
                String email = credentials.getString("email");
                String password = credentials.getString("password");

                userService.authenticateUser(email, password, res -> {
                    if (res.succeeded()) {
                        JsonObject user = res.result();
                        String token = jwtAuth.generateToken(user);

                        ctx.response()
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("token", token).encode());
                    } else {
                        ctx.response().setStatusCode(401).end("Invalid credentials");
                    }
                });
            });
        });


            // Create a room
            router.post("/api/rooms").handler(ctx -> {
                ctx.request().bodyHandler(buffer -> {
                    JsonObject room = buffer.toJsonObject();
                    roomService.createRoom(room, res -> {
                        if (res.succeeded()) {
                            ctx.response().putHeader("Content-Type", "application/json")
                                    .end(new JsonObject().put("roomId", res.result()).encode());
                        } else {
                            ctx.response().setStatusCode(500).end("Failed to create room");
                        }
                    });
                });
            });

            // Get all rooms
            router.get("/api/rooms").handler(ctx -> {
                roomService.getAllRooms(res -> {
                    if (res.succeeded()) {
                        ctx.response().putHeader("Content-Type", "application/json")
                                .end(res.result().toString());
                    } else {
                        ctx.response().setStatusCode(500).end("Failed to retrieve rooms");
                    }
                });
            });

            // Delete a room
            router.delete("/api/rooms/:id").handler(ctx -> {
                String roomId = ctx.pathParam("id");
                roomService.deleteRoom(roomId, res -> {
                    if (res.succeeded()) {
                        ctx.response().end("Room deleted");
                    } else {
                        ctx.response().setStatusCode(500).end("Failed to delete room");
                    }
                });
            });

        router.post("/api/private/send").handler(ctx -> {
            ctx.request().bodyHandler(buffer -> {
                JsonObject msg = buffer.toJsonObject();
                privateChatService.savePrivateMessage(msg, res -> {
                    if (res.succeeded()) {
                        ctx.response().setStatusCode(201).end("Message sent");
                    } else {
                        ctx.response().setStatusCode(500).end("Failed to send message");
                    }
                });
            });
        });

        router.get("/api/private/messages/:user1/:user2").handler(ctx -> {
            String user1 = ctx.pathParam("user1");
            String user2 = ctx.pathParam("user2");

            privateChatService.getPrivateMessages(user1, user2, res -> {
                if (res.succeeded()) {
                    ctx.response().putHeader("Content-Type", "application/json")
                            .end(res.result().toString());
                } else {
                    ctx.response().setStatusCode(500).end("Failed to retrieve messages");
                }
            });
        });


    }

    private void handleWebSocket(ServerWebSocket webSocket) {
        if (!"/ws/chat".equals(webSocket.path())) {
            webSocket.reject();
            return;
        }
        String query = webSocket.query();
        if (query == null || !query.contains("token=")) {
            webSocket.reject();
            return;
        }

        String token = query.split("token=")[1].split("&")[0];

        jwtAuth.authenticate(new JsonObject().put("token", token))
                .onSuccess(user -> {
                    String room = query.contains("room=") ? query.split("room=")[1] : "default";
                    roomConnections.computeIfAbsent(room, r -> ConcurrentHashMap.newKeySet()).add(webSocket);

                    webSocket.textMessageHandler(message -> {
                        JsonObject json = new JsonObject(message);
                        String sender = json.getString("sender");
                        String content = json.getString("content");

                        Message chatMessage = new Message(sender, content, room);
                        chatService.broadcastMessage(chatMessage);
                    });

                    webSocket.closeHandler(v -> {
                        roomConnections.get(room).remove(webSocket);
                    });
                })
                .onFailure(err -> {
                    webSocket.reject();
                });

        String type = webSocket.query().contains("type=") ?
                webSocket.query().split("type=")[1].split("&")[0] : "";

        String userId = webSocket.query().contains("userId=") ?
                webSocket.query().split("userId=")[1].split("&")[0] : "";

        if ("private".equals(type)) {
            // Register this user's webSocket
            userConnections.put(userId, webSocket);

            webSocket.textMessageHandler(message -> {
                JsonObject json = new JsonObject(message);
                String to = json.getString("to");
                ServerWebSocket recipientSocket = userConnections.get(to);

                if (recipientSocket != null && !recipientSocket.isClosed()) {
                    recipientSocket.writeTextMessage(json.encode());
                }

                // Optionally save the private message to MongoDB
                privateChatService.savePrivateMessage(json, res -> {
                    if (res.failed()) {
                        System.err.println("Failed to save private message: " + res.cause().getMessage());
                    }
                });
            });

            webSocket.closeHandler(v -> {
                userConnections.remove(userId);
            });

            webSocket.accept();
            return;
        }

    }

    private String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hashBytes);
    }

    private boolean checkPassword(String inputPassword, String storedHash) throws NoSuchAlgorithmException {
        String inputHash = hashPassword(inputPassword);
        return storedHash.equals(inputHash);
    }


}
