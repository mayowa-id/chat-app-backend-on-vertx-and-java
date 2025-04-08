# Yappity Chat App Backend on Vertx and Java

A high-performance, real-time chat backend built using the [Eclipse Vert.x](https://vertx.io/) toolkit with WebSocket support, JWT authentication, MongoDB for message persistence, Redis for pub/sub and session handling, and RESTful endpoints for chat history and user management.

---![Features of the Messaging System - visual selection](https://github.com/user-attachments/assets/ca1db6ea-19be-4bcf-98bc-d4d6e32a216c)


##  Features

- **WebSocket-based real-time messaging**
- **JWT authentication** (login, signup, token validation)
- **Room-based messaging system**
- **MongoDB** persistence for messages
- **Redis** for pub/sub and session tracking
- **Delivery receipts** and **read receipts**
- **Typing indicators**
- **Message pagination**
- **REST API for retrieving messages**
- **Postman Collection** for API testing

---

##  Project Structure

```
├── src/main/java/org/example
│   ├── MainVerticle.java
│   ├── verticles/
│   │   ├── HttpServerVerticle.java
│   │   └── ChatVerticle.java
│   ├── services/
│   │   └── ChatService.java
│   ├── handlers/
│   │   └── WebSocketHandler.java
│   ├── models/
│   │   └── Message.java
├── webroot/            # Static frontend (optional)
├── docs/
│   └── Vertx-Chat-Application-API.postman_collection.json
├── README.md
└── pom.xml
```

---

## Tech Stack

- **Vert.x Core** for async programming
- **Vert.x Web** for HTTP server & routing
- **Vert.x Auth JWT** for secure authentication
- **MongoDB** (via `vertx-mongo-client`) for persistence
- **Redis** for session/pub-sub management

---

##  Installation & Running Locally

1. **Clone the Repository**
```bash
git clone https://github.com/your-username/vertx-chat-backend.git
cd vertx-chat-backend
```

2. **Start MongoDB & Redis**
Ensure both services are running locally:
```bash
docker run -d --name mongo -p 27017:27017 mongo
docker run -d --name redis -p 6379:6379 redis
```

3. **Run the Application**
```bash
mvn clean compile exec:java -Dexec.mainClass="org.example.MainVerticle"
```

4. **Test via Postman**
- Import the collection file: `docs/Vertx-Chat-Application-API.postman_collection.json`
- Start testing endpoints

---

##  Available Endpoints

### Authentication
| Method | Endpoint               | Description              |
|--------|------------------------|--------------------------|
| POST   | /api/auth/register     | User registration        |
| POST   | /api/auth/login        | User login with JWT      |

### 💬 Messages
| Method | Endpoint                       | Description                          |
|--------|--------------------------------|--------------------------------------|
| GET    | /api/messages/:room            | Get messages from a room             |
| GET    | /api/messages/:room?limit=20   | Get limited number of messages       |
| GET    | /api/messages/:room?before=ts  | Paginate messages before timestamp   |

### 🔁 WebSocket Events
| Event Type         | Payload Description                        |
|--------------------|--------------------------------------------|
| chat_message       | sender, content, room                     |
| read_receipt       | messageId, room                           |
| delivery_receipt   | messageId, room                           |
| typing             | sender, room                              |


---

## 📌 Notes
- Ensure MongoDB connection string is correctly configured in `ChatService.java`
- Token must be passed in WebSocket query: `/ws/chat?token=...&room=...`
- Messages are automatically saved to MongoDB on send

---

## 📄 License
This project is licensed under the MIT License.

---

##  Acknowledgements
- [Vert.x Project](https://vertx.io/)
- [MongoDB](https://www.mongodb.com/)
- [Redis](https://redis.io/)
- [Postman](https://postman.com/)

---

##  Let's Chat
This backend is part of a complete real-time chat application. Stay tuned for the frontend implementation in React!

