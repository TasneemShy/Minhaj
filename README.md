# Minhaj - Educational Platform API

[![CI](https://github.com/TasneemShy/Minhaj/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/TasneemShy/Minhaj/actions/workflows/ci.yml)

Dependencies are kept current by [Dependabot](.github/dependabot.yml) (weekly update PRs for Maven, GitHub Actions and Docker).

Minhaj is a backend REST API for an online learning platform. It covers stateless JWT authentication, role-based access control (RBAC), courses with lessons, student enrollment, course reviews with ratings, email-based password reset, and interactive OpenAPI documentation. It ships with a Docker Compose setup for running the API alongside PostgreSQL.

---

## Tech Stack

* **Framework:** Spring Boot 4.1.0
* **Language:** Java 25
* **Database:** PostgreSQL
* **Security:** Spring Security & JWT (JSON Web Tokens)
* **ORM:** Hibernate / Spring Data JPA
* **Database Migrations:** Flyway (`spring-boot-starter-flyway`)
* **Validation:** Jakarta Validation (`spring-boot-starter-validation`)
* **Email:** Spring Mail (`spring-boot-starter-mail`), tested with Mailtrap
* **API Documentation:** Springdoc OpenAPI / Swagger UI
* **Containerization:** Docker & Docker Compose
* **Build Tool:** Maven (wrapper included)

---

## Architectural Highlights

* **DTO Pattern with Java Records:** JPA entities never cross the API boundary. Request bodies bind to `record` types with Bean Validation (`CourseRequest`, `LessonRequest`, `ReviewRequest`, `CategoryRequest`, …), so clients cannot set server-owned fields such as `id`, `averageRating`, `reviewCount`, `lessonCount`, `createdAt` or `instructor`. Responses are `record` DTOs (`CourseDTO`, `LessonDTO`, `ReviewDTO`, `CategoryDTO`, `UserDTO`) built with small static `from(...)` factories; reviews name their author by `authorId` and `authorName` only.
* **Centralized Exception Handling:** `@RestControllerAdvice` (`GlobalExceptionHandler`) and Spring Security's entry point / access-denied handler return every error as RFC 9457 Problem Details (see [Error format](#error-format)).
* **JPA Auditing:** Automatic `createdAt` / `updatedAt` timestamps (`@CreatedDate`, `@LastModifiedDate`).
* **Pagination, Filtering & Sorting:** Course listing combines Spring Data's `Pageable` with JPA `Specification`s (`CourseSpecifications`) so any mix of search, category, price, and rating filters becomes a single query.
* **Entity Relationships:** Category → Courses (one-to-many), Course → Lessons (one-to-many), Students ↔ Courses (many-to-many enrollment), Course → Reviews and User → Reviews (one-to-many), and per-student lesson completion records (`LessonProgress`).
* **CORS:** Origin allow-list (`CORS_ALLOWED_ORIGINS`) configured in `SecurityConfig`; wildcards are rejected.
* **Stateless JWT Security:** A request filter validates Bearer tokens and applies role-based route rules.
* **Externalized Secrets:** Mail credentials and the JWT signing key are read from environment variables (or a local `.env` file) instead of being hard-coded.

---

## Features

### 1. Authentication & Security

* User registration with BCrypt password hashing and a welcome email that carries an email verification link. Unverified users can log in but cannot enroll or post reviews until they confirm their address.
* Login that issues a short-lived access token (signed JWT, 15 minutes by default) and a long-lived refresh token (14 days). Refresh tokens are single-use and rotated on every refresh, stored only as SHA-256 hashes, and revoked on logout and on password change or reset; reusing an old one signs the user out everywhere. Tokens stay valid across restarts because the signing key comes from configuration.
* Rate limiting on login, registration and password reset (429 with `Retry-After`).
* Hourly cleanup of expired and revoked tokens: expired email verification and reset tokens are deleted, refresh tokens 7 days after they expire or are revoked (kept that long so reuse can still be detected). Configured with `app.token-cleanup.*`.
* Forgot-password flow: emails a one-time reset link valid for 15 minutes. Only a SHA-256 hash of the token is stored in the database.
* Role-based access control with three roles: students (`STUDENT`, the default on registration), instructors (`INSTRUCTOR`), and administrators (`ADMIN`). Administrators assign roles.
* User profile: view your account, update your name, change your password (current password required), and unenroll from courses (which also clears your progress in them).

### 2. Course Management

* Full CRUD for administrators to create, update, and delete courses.
* Free and paid courses (free courses always have a price of `0`).
* Paginated catalog browsing with case-insensitive search, filters (category, free/paid, price range, minimum rating), and sorting.
* Courses can be grouped into categories that administrators manage.
* Instructors create and manage their own courses and lessons; a course shows its instructor's ID and name (never their email).
* Draft/published lifecycle: new courses are private drafts until their instructor (or an admin) publishes them (see [Course lifecycle](#course-lifecycle-draft--published)).
* Student enrollment and a "my courses" view.

### 3. Lessons

* Administrators and the course's instructor can add, edit, delete and reorder lessons (title and video URL).
* Lessons are ordered: each has a `position` (1, 2, 3, … within its course). New lessons go to the end, deleting a lesson closes the gap, and a reorder sets the whole order at once.
* Anyone can list a course's lessons, in position order; the course's lesson count is tracked automatically.
* Deleting a lesson also deletes students' completion records for it, so progress percentages are recalculated against the remaining lessons.

### 4. Learning Progress

* Enrolled students can mark lessons as complete (or undo it).
* Progress is reported per course as completed/total lessons and a percentage, plus an overview across all enrolled courses.

### 5. Reviews & Ratings

* Logged-in users can review a course with a comment and rating (1–5), once per course.
* Authors can edit their own review; the author or an admin can delete it.
* Each course's average rating (rounded to one decimal) and review count are recalculated from the database after every create, edit, and delete.
* Reviews can be listed per course or per user, paginated (`page`, `size`; defaults `0`/`10`) and sorted newest first.

### 6. Admin Analytics

* Platform overview: user counts per role, free vs paid and published vs draft courses, categories, lessons, enrollments, reviews, average rating, and completed lessons.
* Top courses ranked by enrollments, rating, or review count, plus per-category and per-course statistics (including average lesson completion across enrolled students).
* All figures are aggregated in the database rather than by loading records into memory.

### 7. Interactive API Documentation

* Automatically generated OpenAPI specification.
* Swagger UI with JWT Bearer authentication support for testing in the browser.

---

## API Documentation (Swagger UI)

When the service is running, explore and test the endpoints in the browser:

* **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI Specification JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Both are public. Endpoints are grouped by tag (Auth, Courses, Categories, Lessons, Progress, Reviews, Profile, Instructor, Admin – Users, Admin – Analytics, System). Each operation states who may call it (public, authenticated, enrolled students, owning instructor or admin, admin) and lists the status codes it returns. Error responses share one `ProblemDetail` schema (RFC 9457, `application/problem+json`; see [Error format](#error-format)). Request fields carry descriptions and examples, and query parameters such as `sortBy` on `GET /api/courses` list their allowed values. The version shown is the Maven project version.

> **Testing protected routes in Swagger:**
> 1. Call `POST /api/auth/login` (or `/api/auth/register`) to get a token.
> 2. Click the **Authorize** button at the top of the Swagger page.
> 3. Paste the `accessToken` value (without the `Bearer ` prefix) into the `Value` field and submit.
>
> Operations that need a token show a lock icon; public ones don't. The token's role still decides access, so admin endpoints answer 403 for a student token.

A Postman collection is also included in the `postman/` folder.

---

## API Endpoints Reference

### Authentication (`/api/auth`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Register a new account and get a token pair | Public |
| `POST` | `/api/auth/login` | Log in and get a token pair | Public |
| `POST` | `/api/auth/refresh` | Exchange a refresh token for a new token pair (body: `{"refreshToken"}`) | Public |
| `POST` | `/api/auth/logout` | Revoke a refresh token; answers `204` (body: `{"refreshToken"}`) | Public |
| `POST` | `/api/auth/verify-email` | Confirm the email address (body: `{"token"}` from the emailed link) | Public |
| `POST` | `/api/auth/resend-verification` | Email a new verification link (body: `{"email"}`) | Public |
| `POST` | `/api/auth/forgot-password` | Email a password reset token (body: `{"email"}`) | Public |
| `POST` | `/api/auth/reset-password` | Set a new password (body: `{"token", "newPassword"}`) | Public |

**Tokens:** login, register and refresh answer

```json
{
  "message": "Login successful",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "q3J0dX4m1YvR8c2l0nP6sT9wZbE5aHkLxGfUoNiCjDy",
  "expiresIn": 900,
  "role": "STUDENT",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

* `accessToken` goes in the `Authorization: Bearer ...` header. It lives `app.jwt.access-ttl` (default `15m`, env `JWT_ACCESS_TTL`); `expiresIn` is that lifetime in seconds.
* `refreshToken` lives `app.jwt.refresh-ttl` (default `14d`, env `JWT_REFRESH_TTL`). Send it to `POST /api/auth/refresh` before or after the access token expires to get a new pair. Each refresh token works once: the response carries its replacement. Presenting a refresh token that was already used (or revoked) is treated as theft: all of that user's refresh tokens are revoked and the call answers `401`, so every device has to log in again.
* `POST /api/auth/logout` revokes the refresh token it is given. Changing the password (`PUT /api/profile/password`) or resetting it revokes all of the user's refresh tokens.
* Access tokens are stateless and are **not** revoked by logout or a password change: one already issued keeps working until it expires (at most `access-ttl`). Clients should discard it on logout. That is the trade-off for not looking up every request in the database; keep `access-ttl` short.
* **Deprecated:** `token` is the same value as `accessToken` and is only kept so existing clients keep working. It will be removed in the release after next; switch to `accessToken`.

**Email verification:** new accounts start unverified. Registration still returns tokens, and the welcome email links to `<FRONTEND_URL>/verify-email?token=<token>`; the frontend sends that token to `POST /api/auth/verify-email`. The link is valid for 24 hours and works once; only a SHA-256 hash of the token is stored. `POST /api/auth/resend-verification` sends a new link (replacing the old one) and always gives the same answer, whether or not the email is registered or already verified. Completing a password reset also marks the email as verified, since the reset link proves the same thing.

Until the email is verified, exactly two actions answer `403` (Problem Details): enrolling in a course (`POST /api/courses/{id}/enroll`) and posting a review (`POST /api/courses/{courseId}/reviews`). Everything else (browsing, profile, password change, progress in courses already joined) works. `GET /api/profile` and the admin user list include `emailVerified`. Accounts that existed before verification was introduced were marked verified by the `V4` migration.

**Password reset flow:** `forgot-password` always gives the same answer, whether or not the email is registered. For a registered email it sends a link to `<FRONTEND_URL>/reset-password?token=<token>` (`FRONTEND_URL` defaults to `http://localhost:5173`). The backend does not serve that page: the frontend reads `token` from the URL, asks for the new password and sends `POST /api/auth/reset-password` with `{"token", "newPassword"}`. The token is valid for 15 minutes and can be used once; asking again replaces the previous token.

**Rate limits:** the public auth endpoints are rate limited per client, in memory (token buckets per app instance):

| Endpoint | Default limit (`application.properties`) |
| --- | --- |
| `POST /api/auth/login` | 5 per minute per email + client IP (`app.rate-limit.login-per-email`), and 20 per minute per client IP across all emails (`app.rate-limit.login-per-ip`) |
| `POST /api/auth/register`, `/forgot-password`, `/reset-password`, `/resend-verification` | 10 per minute per client IP, counted separately for each endpoint (`app.rate-limit.per-ip`) |

Over the limit the API answers `429 Too Many Requests` (Problem Details) with a `Retry-After` header in seconds. The window is `app.rate-limit.window` (default `1m`); `app.rate-limit.enabled=false` turns limiting off. The per-email login limit is also per IP, so someone guessing a password cannot lock the real owner out from another network.

The client IP is the TCP peer address (`request.getRemoteAddr()`). `X-Forwarded-For` is ignored by default because any client can send it and would get a fresh limit with every request. If the API runs behind exactly one reverse proxy that sets that header, set `app.rate-limit.trust-forwarded-for=true` (the right-most address is used) or `server.forward-headers-strategy=native`. Limits are per instance: with several replicas each one counts on its own.

### Course Catalog & Learning

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/courses` | List published courses (filters and sorting below) | Public |
| `GET` | `/api/courses/{id}` | Get a course's details (drafts: owner and admins only) | Public |
| `GET` | `/api/courses/{courseId}/lessons` | List a course's lessons, ordered by `position` (drafts: owner and admins only) | Public |
| `POST` | `/api/courses/{id}/enroll` | Enroll the current user in a published course | Authenticated, verified email |
| `GET` | `/api/my-courses` | List the current user's enrolled courses, with each course's `status` (an empty list when there are none) | Authenticated |
| `GET` | `/api/categories` | List all categories | Public |

#### Course list query parameters

| Parameter | Example | Description |
| --- | --- | --- |
| `page`, `size` | `page=0&size=10` | Pagination (defaults `0` and `10`) |
| `search` | `search=spring` | Case-insensitive title search |
| `categoryId` | `categoryId=2` | Only courses in this category |
| `free` | `free=true` | Only free (`true`) or paid (`false`) courses |
| `minPrice`, `maxPrice` | `minPrice=10&maxPrice=50` | Price range (inclusive) |
| `minRating` | `minRating=4` | Minimum average rating |
| `instructorId` | `instructorId=3` | Only courses taught by this instructor |
| `sortBy` | `sortBy=price` | `createdAt` (default), `title`, `price`, `averageRating`, or `reviewCount` |
| `direction` | `direction=asc` | `asc` or `desc` (default) |

### Profile (`/api/profile`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/profile` | Get the current user (`id`, `name`, `email`, `role`, `emailVerified`) | Authenticated |
| `PUT` | `/api/profile` | Update your name (body: `{"name"}`); email and role can't be changed here | Authenticated |
| `PUT` | `/api/profile/password` | Change your password (body: `{"currentPassword", "newPassword"}`, new password at least 6 characters) | Authenticated |
| `DELETE` | `/api/profile/courses/{courseId}` | Unenroll from a course and delete your progress in it | Authenticated |

### Learning Progress

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/courses/{courseId}/lessons/{lessonId}/complete` | Mark a lesson complete | Enrolled students |
| `DELETE` | `/api/courses/{courseId}/lessons/{lessonId}/complete` | Mark a lesson not complete | Enrolled students |
| `GET` | `/api/courses/{courseId}/progress` | Progress in one course | Enrolled students |
| `GET` | `/api/my-courses/progress` | Progress across all enrolled courses | Authenticated |

### Reviews

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/courses/{courseId}/reviews` | List a course's reviews, newest first (`?page=0&size=10`; drafts: owner and admins only) | Public |
| `POST` | `/api/courses/{courseId}/reviews` | Review a published course (body: `{"comment", "rating"}`) | Authenticated, verified email |
| `PUT` | `/api/courses/{courseId}/reviews/{reviewId}` | Edit your review (body: `{"comment", "rating"}`) | Review author |
| `DELETE` | `/api/courses/{courseId}/reviews/{reviewId}` | Delete a review | Review author or Admin |
| `GET` | `/api/users/{userId}/reviews` | List a user's reviews, newest first (`?page=0&size=10`) | Authenticated |

### Instructor (`/api/instructor`)

Instructors can only modify their own courses (`403` otherwise); administrators can use these endpoints on any course.

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/instructor/courses` | List my courses, drafts included (paged with `page`, `size`; `?status=DRAFT\|PUBLISHED` to filter) | Instructor, Admin |
| `POST` | `/api/instructor/courses` | Create a draft course owned by me (body: see [Course request body](#course-request-body)) | Instructor, Admin |
| `PUT` | `/api/instructor/courses/{id}` | Update my course | Instructor (owner), Admin |
| `POST` | `/api/instructor/courses/{id}/publish` | Publish my course (needs at least one lesson) | Instructor (owner), Admin |
| `POST` | `/api/instructor/courses/{id}/unpublish` | Move my course back to draft | Instructor (owner), Admin |
| `DELETE` | `/api/instructor/courses/{id}` | Delete my course | Instructor (owner), Admin |
| `POST` | `/api/instructor/courses/{courseId}/lessons` | Add a lesson to the end of my course (body: `{"title", "videoUrl"}`) | Instructor (owner), Admin |
| `PUT` | `/api/instructor/courses/{courseId}/lessons/{lessonId}` | Edit a lesson of my course (body: `{"title", "videoUrl"}`) | Instructor (owner), Admin |
| `DELETE` | `/api/instructor/courses/{courseId}/lessons/{lessonId}` | Delete a lesson of my course (see [Lesson ordering](#lesson-ordering)) | Instructor (owner), Admin |
| `PUT` | `/api/instructor/courses/{courseId}/lessons/order` | Reorder my course's lessons (body: `{"lessonIds": [...]}`) | Instructor (owner), Admin |

### Administration (`/api/admin`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/admin/courses` | Create a draft course (body: see [Course request body](#course-request-body)) | Admin |
| `PUT` | `/api/admin/courses/{id}` | Update a course | Admin |
| `POST` | `/api/admin/courses/{id}/publish` | Publish any course (needs at least one lesson) | Admin |
| `POST` | `/api/admin/courses/{id}/unpublish` | Move any course back to draft | Admin |
| `DELETE` | `/api/admin/courses/{id}` | Delete a course | Admin |
| `POST` | `/api/admin/courses/{courseId}/lessons` | Add a lesson to the end of a course (body: `{"title", "videoUrl"}`) | Admin |
| `PUT` | `/api/admin/courses/{courseId}/lessons/{lessonId}` | Edit a lesson (body: `{"title", "videoUrl"}`) | Admin |
| `DELETE` | `/api/admin/courses/{courseId}/lessons/{lessonId}` | Delete a lesson (see [Lesson ordering](#lesson-ordering)) | Admin |
| `PUT` | `/api/admin/courses/{courseId}/lessons/order` | Reorder a course's lessons (body: `{"lessonIds": [...]}`) | Admin |
| `GET` | `/api/admin/users` | List all users (passwords never returned) | Admin |
| `PUT` | `/api/admin/users/{id}/role` | Set a user's role (body: `{"role": "STUDENT" \| "INSTRUCTOR" \| "ADMIN"}`) | Admin |
| `POST` | `/api/admin/categories` | Create a category (body: `{"name", "description"}`) | Admin |
| `PUT` | `/api/admin/categories/{id}` | Rename or describe a category | Admin |
| `DELETE` | `/api/admin/categories/{id}` | Delete a category (only if no courses use it) | Admin |

#### Lesson ordering

Every lesson has a `position` (1-based, unique within its course) that is returned in `LessonDTO` and decides the order of `GET /api/courses/{courseId}/lessons`.

* `POST .../lessons` puts the new lesson at the end (highest position + 1).
* `PUT .../lessons/{lessonId}` changes only `title` and `videoUrl`; the position stays.
* `DELETE .../lessons/{lessonId}` removes the lesson and its completion records, moves the following lessons up one place and lowers the course's `lessonCount`.
* `PUT .../lessons/order` takes every lesson ID of the course exactly once, in the new order, and assigns positions 1..n. A missing, duplicated or foreign ID is a `400`; the response is the reordered lesson list.

```json
{ "lessonIds": [12, 10, 11] }
```

A lesson ID that is not part of the course in the URL is a `404` for edit and delete.

#### Course lifecycle (draft → published)

Every course has a `status` of `DRAFT` or `PUBLISHED`, returned in `CourseDTO` together with `publishedAt`.

* New courses (`POST /api/admin/courses`, `POST /api/instructor/courses`) start as `DRAFT`. `status` and `publishedAt` in a request body are ignored.
* `POST .../{id}/publish` makes the course `PUBLISHED`. A course with no lessons cannot be published (`400`). Publishing an already published course changes nothing.
* `publishedAt` is set on the **first** publish and kept afterwards, through unpublish and re-publish, so re-publishing does not push a course to the top of the newest-first catalog. It is `null` for a course that was never published.
* `POST .../{id}/unpublish` moves the course back to `DRAFT`.
* Drafts never appear in `GET /api/courses`. `GET /api/courses/{id}`, `.../lessons` and `.../reviews` return a draft only to its instructor and admins (send the bearer token); everyone else gets the same `404` as for a course that does not exist, so a draft's existence is not leaked.
* Enrolling in or reviewing a draft is a `404` for everyone, including its instructor and admins.
* Students who enrolled before a course was unpublished keep their enrollment, progress and reviews. The course stays in their `GET /api/my-courses` (with `"status": "DRAFT"`) and progress endpoints, but they cannot open its detail page or post new reviews until it is published again.
* `GET /api/instructor/courses` lists drafts and published courses; filter with `?status=DRAFT` or `?status=PUBLISHED`.
* The analytics overview reports `publishedCourses` and `draftCourses` next to `totalCourses`.
* Courses that existed before the lifecycle was added were migrated to `PUBLISHED` with `publishedAt = createdAt` (Flyway `V3__course_status.sql`).

#### Course request body

`POST` and `PUT` on `/api/admin/courses` and `/api/instructor/courses` take the same body. Every field except `title` is optional; `PUT` replaces all of them. A free course always has price `0`.

```json
{
  "title": "Spring Boot Mastery",
  "description": "Build REST APIs with Spring Boot",
  "price": 49.99,
  "free": false,
  "imageUrl": "https://example.com/spring.png",
  "categoryId": 1
}
```

> **Breaking change:** the category used to be sent nested as `"category": {"id": 1}`. It is now a flat `categoryId`; the old nested form is ignored (the course ends up uncategorized). Server-owned fields (`id`, `averageRating`, `reviewCount`, `lessonCount`, `createdAt`, `updatedAt`, `instructor`) are ignored if sent. Course responses always use the `CourseDTO` shape: `categoryId`/`categoryName` and `instructorId`/`instructorName` instead of nested objects, and `isFree` instead of `free`.

> **Note:** the role is stored in the JWT, so a user whose role was changed keeps their old permissions until they get a new access token (by refreshing or logging in again), at most `app.jwt.access-ttl` later.

### Analytics (`/api/admin/analytics`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/admin/analytics/overview` | Platform totals: users (and per role), courses (free/paid, published/draft), categories, lessons, enrollments, reviews, average rating, completed lessons | Admin |
| `GET` | `/api/admin/analytics/top-courses` | Top courses; `by=enrollments` (default), `rating` or `reviews`, `limit=1..50` (default `5`) | Admin |
| `GET` | `/api/admin/analytics/categories` | Course count, enrollments and average rating per category, plus an `Uncategorized` row | Admin |
| `GET` | `/api/admin/analytics/courses/{id}` | One course's enrollments, lessons, reviews, average rating and average completion % | Admin |

### System

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/health` | Check that the service is running | Public |
| `GET` | `/actuator/health` | Overall health (Spring Boot Actuator). Anonymous callers get only `{"status":"UP"}`; ADMIN tokens also see components (database, disk space, …) | Public (details: ADMIN) |
| `GET` | `/actuator/health/liveness` | Liveness probe: the app is running | Public |
| `GET` | `/actuator/health/readiness` | Readiness probe: the app can serve traffic and the database is reachable | Public |
| `GET` | `/actuator/info` | Application name, description, version and build info | Public |

Only `health` and `info` are exposed over HTTP; all other actuator endpoints are disabled on the web. The mail health check is turned off, so a Mailtrap outage never marks the app as down.

### Error format

Every error response uses [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) with content type `application/problem+json`: `type`, `title` (the HTTP reason phrase), `status`, `detail` (a human-readable message) and `instance` (the request path). Validation failures add an `errors` object mapping each invalid field to its message:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed.",
  "instance": "/api/auth/register",
  "errors": {
    "email": "Invalid email format",
    "password": "Password must be at least 6 characters long"
  }
}
```

Common statuses: `400` invalid input, `401` missing/invalid token or wrong login, `403` authenticated but not allowed, `404` not found, `409` email already registered, `500` unexpected error (the detail never includes internal exception messages).

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/TasneemShy/Minhaj.git
cd Minhaj
```

### 2. Configure environment variables

Secrets are kept out of the code. Copy the example file, then fill in your SMTP (e.g. [Mailtrap](https://mailtrap.io)) credentials and a JWT signing key:

```bash
cp .env.example .env
```

```properties
MAIL_USERNAME=your-mailtrap-username
MAIL_PASSWORD=your-mailtrap-password
JWT_SECRET=your-base64-jwt-secret
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
FRONTEND_URL=http://localhost:5173
JWT_ACCESS_TTL=15m
JWT_REFRESH_TTL=14d
```

`CORS_ALLOWED_ORIGINS` is a comma-separated list of the exact browser origins allowed to call the API (optional; defaults to the local React/Vite dev servers shown above). Set it to your frontend's URL in production, e.g. `https://app.example.com`. A `*` wildcard is rejected and the app refuses to start.

`FRONTEND_URL` is the base URL of the frontend (optional; defaults to `http://localhost:5173`). Password reset emails link to `<FRONTEND_URL>/reset-password?token=...` and verification emails to `<FRONTEND_URL>/verify-email?token=...`, so the frontend must serve both pages.

`JWT_ACCESS_TTL` and `JWT_REFRESH_TTL` set the access and refresh token lifetimes (optional; `15m` and `14d` by default, any Spring duration such as `30m`, `12h`, `7d`).

Generate a JWT secret (a base64-encoded 256-bit key) with:

```bash
openssl rand -base64 32
```

`.env` is ignored by git. The app reads it automatically when run locally, and Docker Compose passes it into the container.

### Option A: Run with Docker (recommended)

Prerequisites: Docker (no local JDK or Maven needed — the image is built inside Docker) and a `.env` file in the project root (see `.env.example`).

```bash
docker compose up --build
```

This builds the `minhaj-backend` image with a multi-stage Dockerfile and starts:

* **PostgreSQL** (`minhaj-db`), reachable from your machine on port `5433`.
* **The API** (`minhaj-app`) on [http://localhost:8080](http://localhost:8080), started once the database is healthy. It runs as a non-root user, and `.env` is passed in at runtime, never copied into the image.

The `app` service has a Docker healthcheck that calls `/actuator/health/readiness` (with a 40-second start period for Spring Boot and Flyway), so `docker compose ps` shows `minhaj-app` as `healthy` once the API is up and can reach the database.

After code changes, run `docker compose up --build` again to rebuild the image (dependencies are cached, so only changed layers rebuild). To wipe the database and start fresh, run `docker compose down -v`.

### Option B: Run locally

Prerequisites: JDK 25 and PostgreSQL running on port `5432` with a `minhaj_db` database.

1. Set your database credentials in `src/main/resources/application.properties`:

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/minhaj_db
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

2. Start the application:

   ```bash
   ./mvnw spring-boot:run
   ```

### Running tests

```bash
./mvnw test
```

* **Docker must be running.** The integration tests start a throwaway `postgres:15-alpine` container with [Testcontainers](https://testcontainers.com/) (one container shared by the whole run); Flyway migrates it and Hibernate validates the schema, just like at startup.
* **No local database and no `.env` are needed.** Tests run with the `test` profile (`src/test/resources/application-test.properties`), which supplies a test-only JWT secret and dummy mail settings; the mail sender is mocked, so no email is ever sent. Your local `minhaj_db` is never touched.
* Integration tests (`src/test/java/.../integration`) drive the real API end to end through MockMvc with real security and JWTs; every test starts from empty tables. `@WebMvcTest` slice tests (`.../web`) and plain unit tests run without Docker.
* Run one class with `./mvnw test -Dtest=ReviewIntegrationTest`.

### Database migrations

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Migrations live in `src/main/resources/db/migration` and run automatically on startup; Hibernate only validates the schema against the entities (`ddl-auto=validate`) and never changes it.

* To change the schema, add a new file with the next version number, e.g. `V2__add_course_language.sql`, and update the entity in the same change. If the entities and the migrations disagree, the app fails to start.
* Never edit a migration that has already been applied anywhere; Flyway checksums each file and will refuse to start. Write a new migration instead.
* Databases created before Flyway was introduced (by the old `ddl-auto=update`) are marked as baselined at `V1` on first start and keep their data; empty databases run `V1__baseline.sql`.

---

## Roadmap

* [ ] AWS S3 integration for course images and lesson media.
