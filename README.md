# Football Match Updates - STOMP Client-Server ⚽📡

A robust client-server application implementing the **STOMP (Simple Text Oriented Messaging Protocol)** to provide real-time updates and event subscriptions for football matches.

## 📖 Overview
This project was developed during my second year of Computer Science studies at Ben-Gurion University of the Negev. It demonstrates advanced network programming, concurrent execution, and protocol implementation. The system allows multiple clients to concurrently connect to a central server, subscribe to specific football match channels, and receive or broadcast real-time events.

## ✨ Key Features & Technical Highlights
* **Client-Server Architecture:** * **Server (Java):** A scalable server handling multiple concurrent client connections, utilizing thread-safe data structures and efficient message routing mechanisms.
  * **Client (C++):** A multi-threaded client managing network I/O and user input concurrently, ensuring responsive interactions without blocking.
* **STOMP Protocol Implementation:** Full parsing and handling of STOMP frames (`CONNECT`, `SUBSCRIBE`, `SEND`, `UNSUBSCRIBE`, `DISCONNECT`) to manage client sessions reliably.
* **Pub/Sub Messaging Pattern:** Implemented a Publish-Subscribe mechanism where clients dynamically receive only the updates for the specific matches they are subscribed to.

## 🛠️ Technologies & Concepts
* **Languages:** Java (Server), C++ (Client)
* **Networking:** TCP/IP Sockets, STOMP Protocol
* **Concepts:** Network Programming, Concurrency, Multi-threading, Message Brokers, Object-Oriented Design.

## 🚀 Getting Started

### Prerequisites
* Java Development Kit (JDK) 11 or higher
* C++ Compiler (GCC/g++)
* Make & Maven (or equivalent build tools)

### Running the Server
```bash
# Navigate to the server directory
cd server

# Compile and run the server
mvn compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 tpc"
```

### Running the Client
```bash
# Navigate to the client directory
cd client

# Compile the client
make

# Run the executable
./bin/StompWCIClient 127.0.0.1 7777
```
