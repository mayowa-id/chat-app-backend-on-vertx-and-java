package org.example.models;

import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.UUID;

public  class User {
    private String id;
    private String username;
    private String passwordHash;
    private String email;
    private Instant createdAt;
    private Instant lastSeen;
    private boolean online;
    public User() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.lastSeen = Instant.now();
        this.online = false;
    }

    public User(String username, String passwordHash, String email) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }
    public JsonObject toJson() {
        return new JsonObject()
                .put("_id", id)
                .put("username", username)
                .put("passwordHash", passwordHash)
                .put("email", email)
                .put("createdAt", createdAt.toString())
                .put("lastSeen", lastSeen.toString())
                .put("online", online);
    }

    public static User fromJson(JsonObject json) {
        User user = new User();
        user.id = json.getString("_id", UUID.randomUUID().toString());
        user.username = json.getString("username");
        user.passwordHash = json.getString("passwordHash");
        user.email = json.getString("email");
        user.createdAt = Instant.parse(json.getString("createdAt", Instant.now().toString()));
        user.lastSeen = Instant.parse(json.getString("lastSeen", Instant.now().toString()));
        user.online = json.getBoolean("online", false);
        return user;
    }


    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }
    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isOnline() {
        return online;
    }
    public void setOnline(boolean online) {
        this.online = online;
    }
}
