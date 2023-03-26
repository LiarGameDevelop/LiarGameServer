# What is Liar Game?
The Liar Game is a game to find the liar. Each round consists of one liar and other citizens. When the game starts, the categories are open to everyone, and the keywords are open to citizens except Liar. The game progress is as follows. Everyone takes turns explaining keywords. Liar must infer keywords from other people's explanations and pretend to be a citizen. Liar also has to explain, of course. When the explanation is over, people find a liar through voting.

# What is your project?
This is a web-based Liar Game.

# LiarGameServer
This repository implements a server for our web-based Liar Game.

* Tech stack
    * Spring boot
    * Mysql
    * Spring security
    * Spring Data JPA
    * websocket(STOMP)
    * Message queue(RabbitMQ)

## BUILD
The server can be built with gradle.

Windows
```
gradlew clean build
```
Linux
```
./gradlew clean build
```

## Execute
Since the main server is linked with db, *the jasypt password may be required.* You will need to build the db yourself and modify application.yml file. See application.yml for details.

```
java -jar liar-0.0.1-SNAPSHOT.jar
```

## Architecture
### 1. ERD
![ERD](./doc/image/erd.png)

### 2. State Diagram
This is game server state diagram.
```mermaid
stateDiagram
    state "Before Start" as bs
    state "Before Round" as br
    state "Select Liar" as sl
    state "Open Keyword" as ok
    state "In Progress" as ip
    state "Vote Liar" as vl
    state "Open Liar" as ol
    state "Liar Answer" as la
    state "Publish Score" as ps
    state "Publish Ranking" as pr
    state "End game" as eg

    [*] --> bs
    bs --> br
    br --> sl
    sl --> ok
    ok --> ip
    ip --> vl
    vl --> vl: No unique liar candidate
    vl --> ol: Unique liar candidate from all client vote
    ol --> la
    la --> ps: liar's guess about real keyword
    ps --> pr
    pr --> eg
    eg --> [*]
```

### 3. Sequence Diagram
```mermaid
sequenceDiagram
    autonumber
    actor client as client
    participant room as Room server
    participant mq as Message queue
    participant stomp as STOMP server

    alt host
    client->>+room: [HTTP]request to make a room(username,generated password)
    room->>-client: room info, user info, token
    else guest
    client->>+room: [HTTP]request to enter the room
    room->>-client: user info, token
    end

    client->>+stomp: request to connect stomp server
    stomp->>-client: stomp connection info

    client->>mq: request to subscribe
    alt authorized client(with token)
    mq->>stomp: send message
    stomp->>client: permit subscription
    else unauthorized client(without token)
    mq->>stomp: send message from client
    stomp->>client: refuse subscription
    end

    loop game progress
    client->>mq: game progress request
    activate client
    mq->>stomp: send message
    stomp->>client: reply game infomation
    deactivate client
    end

    client->>mq: disconnect
    mq->>stomp: send discconect message
    stomp->>room: delete room if client is room owner
```

This is entire game flow.
#### 1~4) Create/Enter a room and response from a server
Users can create or join a room. When creating a room, the *username* is input from the user, and the *password* is a randomly generated string from the client and passed to the server. The game does not require game membership registration, so there is no field for user input password. The server creates **jwt** based on the input username and password and sends the room information and user information. jwt is used for websocket communication security. **Through jwt, users are given permission to access only the rooms they are allowed to**. A similar action is taken when entering a room. At this time, you need the room ID, username and password to enter.

#### 5~6) Request to connect websocket
With jwt and room infomation, client need to request to connect server. It makes client connect to each other by subscribing messages and sending message through STOMP broker.

#### 7~11) Subscribe with authorization
Through JWT, client can only receive messages from the room client belongs to(authorization)

#### 12~14) Enjoy the liar game
The game is composed of states. When a client makes a request to the server according to the state, it enters the server through the message queue and, depending on the situation, delivers the message to all clients (public message) or to the requesting client (private message).

#### 15~16) Disconnect
If the client disconnects from the websocket server for any reason, the stomp server emits a disconnect event and deletes all related information.