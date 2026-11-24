# Room API

Spring Boot REST API for rooms and reservations, running on MySQL through Spring Data JPA,
protected with Spring Security (HTTP Basic and JWT), with roles (rule D) and owners (rule C).

**JDK:** 21  **Database:** MySQL 8

## Database setup

Run as root once (the root password is not stored in the project):

```sql
CREATE DATABASE roomdb;
CREATE USER 'roomapp'@'localhost' IDENTIFIED BY 'roomapp1234';
GRANT ALL PRIVILEGES ON roomdb.* TO 'roomapp'@'localhost';
```

Before testing, reset the tables and sample data with `reset.sql`. It drops and recreates
`room`, `app_user`, `refresh_token` and `reservation`, and inserts 3 rooms, 3 accounts and 5 reservations.
Use either way:

**Terminal** (from the project folder):

```bash
mysql -u roomapp -p roomdb < reset.sql
```

On macOS, if you get `command not found: mysql`, use the full path:

```bash
/usr/local/mysql/bin/mysql -u roomapp -p roomdb < reset.sql
```

Enter `roomapp1234` when asked for the password.

**MySQL Workbench:**

1. Open a connection as `roomapp` (password `roomapp1234`).
2. Open `reset.sql` in any editor, copy everything, and paste it into a new SQL tab in Workbench.
3. Click the lightning bolt ⚡ (Execute) to run the whole script.
4. Check with `SELECT * FROM roomdb.reservation;` (5 reservations, each with an `owner_id`) and `SELECT * FROM roomdb.app_user;` (3 accounts, passwords stored as BCrypt hashes).

Spring does not create the tables: `spring.jpa.hibernate.ddl-auto=validate` only checks that the entities match them.
The app will not start on the old Week 10 tables (no `owner_id` column), so run the new `reset.sql` first.

## How to run

Run `reset.sql` first, then:

```bash
./gradlew bootRun        # macOS / Linux
gradlew.bat bootRun      # Windows
```

The server starts on http://localhost:8080. Swagger UI: http://localhost:8080/swagger-ui.html

## Settings from the environment

Secrets and origins are read from environment variables. Each has a default that is for class only.

| Variable | Property | Class-only default |
|---|---|---|
| `DB_PASSWORD` | `spring.datasource.password` | `roomapp1234` |
| `JWT_SECRET` | `app.jwt.secret` | `week10-dev-only-secret-change-me-0123456789` (at least 32 bytes) |
| `CORS_ORIGINS` | `app.cors.allowed-origins` | `http://localhost:5500,http://127.0.0.1:5500` (comma-separated) |

To set your own values before starting:

```bash
# macOS / Linux
export DB_PASSWORD="your-db-password"
export JWT_SECRET="a-long-random-secret-of-at-least-32-bytes"
export CORS_ORIGINS="http://localhost:5500"
./gradlew bootRun
```

```powershell
# Windows PowerShell
$env:JWT_SECRET="a-long-random-secret-of-at-least-32-bytes"
.\gradlew.bat bootRun
```

In IntelliJ: Run → Edit Configurations → Environment variables.

## Authentication

### Getting a token

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "username": "mina", "password": "mina1234" }
```

Answer (200):

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 1800, "refreshToken": "..." }
```

The access token is a JWT signed with HS256, valid for 30 minutes. Its `role` claim (`USER` or `ADMIN`)
becomes the authority `ROLE_USER` or `ROLE_ADMIN`, the same authority HTTP Basic gets from the database.

### Sending a token

Put it in the `Authorization` header of every protected request:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

HTTP Basic also works: `Authorization: Basic <base64 of username:password>`,
for example `Authorization: Basic bWluYTptaW5hMTIzNA==` for `mina:mina1234`.

### Signing up

```http
POST http://localhost:8080/api/auth/signup
Content-Type: application/json

{ "username": "lee", "password": "lee12345" }
```

- `username`: 3 to 20 lowercase letters or digits, must be unique (409 if taken)
- `password`: 8 to 72 characters

Every new account gets the role `USER`. A `"role"` in the body is ignored: the client never chooses its own role.

### Sample accounts (created by reset.sql)

| Username | Password | Role | Owns reservations |
|---|---|---|---|
| mina | mina1234 | USER | 1, 3 |
| omar | omar1234 | USER | 2, 4, 5 |
| admin | admin1234 | ADMIN | none |

Passwords are never stored: the `app_user` table holds only BCrypt hashes.

### Refresh tokens (Week 10 optional challenge)

Login also returns a `refreshToken`: a random value stored in the `refresh_token` table, valid for 7 days.

| Method | Path | Body | Answer |
|---|---|---|---|
| POST | `/api/auth/refresh` | `{ "refreshToken": "..." }` | 200, a new access token and a new refresh token; the old one is deleted, so each works only once |
| POST | `/api/auth/logout` | `{ "refreshToken": "..." }` | 204, the refresh token is deleted |

An unknown, used, expired or logged-out refresh token answers 401 `token is invalid or expired`.
Requests 83–88 in `api.http` show the cycle.

## Authorization

Who may call what:

| Method | Path | Who may call it |
|---|---|---|
| GET | `/api/rooms`, `/api/rooms/{id}`, `/api/rooms/count`, `/api/stats` | anyone |
| GET | `/api/rooms/{roomId}/reservations` | anyone |
| POST | `/api/rooms` | admin only (rule D) |
| PUT | `/api/rooms/{id}` | admin only (rule D) |
| DELETE | `/api/rooms/{id}` | admin only (rule D) |
| POST | `/api/rooms/{roomId}/reservations` | any logged-in user; the caller becomes the owner |
| GET | `/api/reservations/mine` | any logged-in user: only their own reservations |
| GET | `/api/reservations/{id}` | the owner or an admin (rule C) |
| PUT | `/api/reservations/{id}` | the owner only, not even an admin (rule C, Lab G) |
| DELETE | `/api/reservations/{id}` | the owner or an admin (rule C) |
| POST | `/api/auth/signup`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` | anyone |
| GET | `/api/auth/me` | any logged-in user |

### Two refusals

- **401 Unauthorized** — we do not know who you are: no credentials, a wrong password, or a broken or expired token.
  Logging in fixes it.
- **403 Forbidden** — we know who you are, but you may not do this. Logging in again changes nothing.

Both use the `ApiError` body. There are two kinds of 403:

| Rule | Checked where | Message |
|---|---|---|
| D — only an admin changes rooms | URL rules in `SecurityConfig` (`hasRole("ADMIN")`) | `admin role required` |
| C — only the owner (or an admin, for reading and cancelling) | `ReservationService`, which loads the reservation first | `reservation 9 belongs to another user` |

Ownership is checked in the service, not in `SecurityConfig`: a URL rule sees `/api/reservations/9` but cannot see
who owns reservation 9 — the service can.

### Order of the checks

For a request on one reservation, the checks run in this order:
**401** (who are you?, in the security filter) → **400** (bad times) → **404** (does it exist?) → **403** (is it yours?) → **409** (overlap).
We look the reservation up before we ask whose it is, so a missing reservation answers 404, not 403.

### The owner comes from the login

A reservation's owner is taken from `authentication.getName()` (the Basic username or the token's `sub`),
never from the request body. A `reservedBy` field in the body is ignored (request 34).
A reservation never overlaps itself: moving it to the same time answers 200 (request 73).

## CORS

Browsers on the origins in `CORS_ORIGINS` may call `/api/**` with `GET`, `POST`, `PUT` and `DELETE`,
the headers `Authorization` and `Content-Type`, and may read the `Location` header.
A preflight from any other origin answers 403 with the plain text `Invalid CORS request` (the CORS filter answers before our code).
CORS only limits browsers: curl and api.http are not affected.

## API Endpoints

| Method | Endpoint | Status | Description |
|---|---|---|---|
| GET | `/api/rooms` | 200 | All rooms; optional `minCapacity`, `keyword`, `sort=capacity` or `sort=name` |
| GET | `/api/rooms/count` | 200 | Number of rooms |
| GET | `/api/stats` | 200 | `{ "rooms": n }` |
| GET | `/api/rooms/{id}` | 200 / 400 / 404 | One room |
| POST | `/api/rooms` | 201 / 400 / 401 / 403 | Create a room |
| PUT | `/api/rooms/{id}` | 200 / 400 / 401 / 403 / 404 | Replace a room's name and capacity |
| DELETE | `/api/rooms/{id}` | 204 / 401 / 403 / 404 / 409 | Delete a room (409 while it has reservations) |
| GET | `/api/rooms/{roomId}/reservations` | 200 / 404 | Reservations of one room, by start time |
| POST | `/api/rooms/{roomId}/reservations` | 201 / 400 / 401 / 404 / 409 | Book a room (409 if the time overlaps) |
| GET | `/api/reservations/mine` | 200 / 401 | My reservations, with room names, in one JOIN |
| GET | `/api/reservations/{id}` | 200 / 400 / 401 / 403 / 404 | One reservation |
| PUT | `/api/reservations/{id}` | 200 / 400 / 401 / 403 / 404 / 409 | Move a reservation to a new time |
| DELETE | `/api/reservations/{id}` | 204 / 401 / 403 / 404 | Cancel a reservation |
| POST | `/api/auth/signup` | 201 / 400 / 409 | Create an account (always USER) |
| POST | `/api/auth/login` | 200 / 400 / 401 | Get a JWT and a refresh token |
| GET | `/api/auth/me` | 200 / 401 | Who is logged in, with authorities |

Reservation bodies (POST and PUT): `{ "startTime": "2026-11-20T10:00:00", "endTime": "2026-11-20T11:00:00" }`.
Reservation responses: `{ "id", "roomId", "roomName", "owner", "startTime", "endTime" }` — `owner` is a username, never the account.

## Testing with api.http

Run `reset.sql`, start the app, then run the requests in `api.http` **in order, from 1 to 82**.
Each request's comment says what it should answer. The results assume the starting state;
a second run without a reset gives different answers.

| Requests | What they check |
|---|---|
| 1–14 | Rooms: reading is public, changes use admin (rule D) |
| 15–25 | Reservations: booking as mina, overlap 409, `/mine`, read and cancel |
| 26–33 | Dirty checking and error bodies |
| 34–41 | Bad input; a `reservedBy` in the body is ignored (34) |
| 42–56 | Authentication: 401s, sign-up, Basic, tokens |
| 57–65 | Rule D: roles from tokens, 403 `admin role required`, 401 for nobody |
| 66–71 | Rule C: owners, 403 `reservation 9 belongs to another user`, 404 before 403 |
| 72–80 | Lab G: moving a reservation (owner only, no self-overlap) |
| 81–82 | CORS preflight: allowed origin 200, other origin 403 |
| 83–88 | Week 10 bonus: refresh and logout (optional) |

## Validation

- Room names must not be blank and must be at most 100 characters.
- Room capacity must be between 1 and 20.
- Reservation start and end times are required, and the start must be before the end.
- Sign-up usernames are 3 to 20 lowercase letters or digits; passwords are 8 to 72 characters.

Invalid fields are listed in the `fieldErrors` property of the error response.

## Error Responses

Every error, including a 401 or 403 from the security filters, uses the same `ApiError` body:

```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "admin role required",
  "path": "/api/rooms",
  "fieldErrors": []
}
```

| Status | Meaning |
|---:|---|
| 200 | Request successful |
| 201 | Resource created successfully |
| 204 | Resource deleted successfully |
| 400 | Invalid request, input validation failure, or a wrong type in the path |
| 401 | Not logged in, wrong username or password, or an invalid or expired token |
| 403 | Logged in, but not allowed: not an admin (rule D) or not the owner (rule C) |
| 404 | Requested resource or endpoint not found |
| 409 | Conflict: an overlapping reservation, a room that still has reservations, or a username that is taken |
| 500 | Unexpected server error (details are logged on the server, never sent to the client) |

## AI use

No AI was used in the development of this project.
