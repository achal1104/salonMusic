# Saloon Song Backend (Java + Spring Boot + MySQL)

REST API that stores your 25-song playlist in MySQL.

## Prerequisites

- Java 17+ (`java -version`)
- Maven 3.8+ (`mvn -version`)
- MySQL Server running locally (`mysql --version`)

## 1. Create the database

```sql
CREATE DATABASE saloon_db;
```

(You can skip this — `createDatabaseIfNotExist=true` in the connection URL
will create it automatically on first run, as long as your MySQL user has
permission to create databases.)

## 2. Set your MySQL credentials

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.username=root
spring.datasource.password=your_mysql_password
```

## 3. Run it

```bash
mvn spring-boot:run
```

On first run this will:
- Auto-create the `songs` table (via `spring.jpa.hibernate.ddl-auto=update`)
- Seed it with your 25 songs (via `data.sql`)

Server runs at **http://localhost:4000**.

> **Important:** after the first successful run, open
> `application.properties` and set `spring.sql.init.mode=never`.
> Otherwise `data.sql` re-runs on every restart and wipes/reinserts the
> table each time (it starts with `DELETE FROM songs;`).

## 4. Add your audio files

Drop your **licensed** mp3 files into:
```
src/main/resources/static/songs/01.mp3
src/main/resources/static/songs/02.mp3
...
src/main/resources/static/songs/25.mp3
```

Spring Boot serves anything in `static/` automatically, so they'll be
reachable at `http://localhost:4000/songs/01.mp3`.

> This project only stores metadata and file paths in MySQL — it does not
> and cannot include the actual copyrighted audio. Supply your own licensed
> files.

## API

| Method | Endpoint          | Description                              |
|--------|-------------------|-------------------------------------------|
| GET    | `/api/songs`      | List all songs (playlist order)           |
| GET    | `/api/songs/{id}` | Get one song                              |
| POST   | `/api/songs`      | Add a song (JSON body: title, artist, duration, src, art?) |
| PUT    | `/api/songs/{id}` | Update a song                             |
| DELETE | `/api/songs/{id}` | Remove a song                             |

Example test once running:
```bash
curl http://localhost:4000/api/songs
```

## Connecting the React frontend

Your Vite frontend (`localhost:5173`) can fetch the playlist from
`http://localhost:4000/api/songs`. CORS is already configured in
`CorsConfig.java` to allow requests from `http://localhost:5173`.

See `frontend-integration-example.jsx` for a ready-to-paste example that
replaces the static `import PLAYLIST from "./playlist.js"` with a live
fetch call.

## Project structure

```
song-backend-java/
├── pom.xml
├── src/main/java/com/saloon/songbackend/
│   ├── SongBackendApplication.java     # entry point
│   ├── model/Song.java                 # JPA entity (maps to `songs` table)
│   ├── repository/SongRepository.java  # Spring Data JPA repository
│   ├── controller/SongController.java  # REST endpoints
│   └── config/CorsConfig.java          # allows the frontend to call this API
└── src/main/resources/
    ├── application.properties          # MySQL connection + server config
    ├── data.sql                        # seeds the 25 songs
    └── static/songs/                   # put your mp3 files here
```
