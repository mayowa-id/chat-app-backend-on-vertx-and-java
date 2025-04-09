package org.example.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Room {
    private Instant createdAt;
    private String id;
    private String name;
    private String description;
    private List<String> memberIds;



    public Room() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.memberIds = new ArrayList<>();
    }

    public Room(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    public JsonObject toJson() {
        return new JsonObject()
                .put("_id", id)
                .put("name", name)
                .put("description", description)
                .put("memberIds", new JsonArray(memberIds))
                .put("createdAt", createdAt.toString());
    }

    public static Room fromJson(JsonObject json) {
        Room room = new Room();
        room.id = json.getString("_id", UUID.randomUUID().toString());
        room.name = json.getString("name");
        room.description = json.getString("description");

        JsonArray memberArray = json.getJsonArray("memberIds", new JsonArray());
        room.memberIds = new ArrayList<>();
        for (int i = 0; i < memberArray.size(); i++) {
            room.memberIds.add(memberArray.getString(i));
        }

        room.createdAt = Instant.parse(json.getString("createdAt", Instant.now().toString()));
        return room;
    }

    // Getters and setters
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }
    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }
    public void addMember(String userId) {
        this.memberIds.add(userId);
    }
    public void removeMember(String userId) {
        this.memberIds.remove(userId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
