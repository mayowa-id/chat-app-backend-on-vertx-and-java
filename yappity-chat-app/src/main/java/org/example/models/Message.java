package org.example.models;

import java.time.Instant;

public class Message {
    private String sender;
    private String content;
    private String room;
    private long timestamp;

    public Message(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    private String id;


    public Message() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public Message(String sender, String content, String room) {
        this.sender = sender;
        this.content = content;
        this.room = room;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
