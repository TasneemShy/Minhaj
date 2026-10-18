# Minhaj - Educational Platform API

Minhaj is a backend REST API for an online learning platform. It covers stateless JWT authentication, role-based access control (RBAC), courses with lessons, student enrollment, course reviews with ratings, email-based password reset, and interactive OpenAPI documentation. It ships with a Docker Compose setup for running the API alongside PostgreSQL.

---

## Tech Stack

* **Framework:** Spring Boot 4.1.0
* **Language:** Java 25
* **Database:** PostgreSQL
* **Security:** Spring Security & JWT (JSON Web Tokens)
* **ORM:** Hibernate / Spring Data JPA
* **Validation:** Jakarta Validation (`spring-boot-starter-validation`)
* **Email:** Spring Mail (`spring-boot-starter-mail`), tested with Mailtrap
* **API Documentation:** Springdoc OpenAPI / Swagger UI
* **Containerization:** Docker & Docker Compose
* **Build Tool:** Maven (wrapper included)

---

## Architectural Highlights

* **DTO Pattern with Java Records:** Persistence entities are kept separate from API responses using Java `record` types (`CourseDTO`).
* **Centralized Exception Handling:** `@ControllerAdvice` (`GlobalExceptionHandler`) turns validation failures and missing resources into structured JSON error responses.
* **JPA Auditing:** Automatic `createdAt` / `updatedAt` timestamps (`@CreatedDate`, `@LastModifiedDate`).
* **Pagination, Filtering & Sorting:** Course listing combines Spring Data's `Pageable` with JPA `Specification`s (`CourseSpecifications`) so any mix of search, category, price, and rating filters becomes a single query.
* **Entity Relationships:** Category → Courses (one-to-many), Course → Lessons (one-to-many), Students ↔ Courses (many-to-many enrollment), Course → Reviews and User → Reviews (one-to-many), and per-student lesson completion records (`LessonProgress`).
* **CORS:** Centralized CORS policy in `SecurityConfig` for frontend and mobile clients.
* **Stateless JWT Security:** A request filter validates Bearer tokens and applies role-based route rules.
* **Externalized Secrets:** Mail credentials and the JWT signing key are read from environment variables (or a local `.env` file) instead of being hard-coded.

---

## Features

### 1. Authentication & Security

* User registration with BCrypt password hashing and a welcome email.
* Login that issues a signed JWT (valid for 24 hours); tokens stay valid across restarts because the signing key comes from configuration.
* Forgot-password flow: emails a one-time reset token valid for 15 minutes.
* Role-based access control distinguishing students (`STUDENT`) from administrators (`ADMIN`).
* User profile: view your account, update your name, change your password (current password required), and unenroll from courses (which also clears your progress in them).

### 2. Course Management

* Full CRUD for administrators to create, update, and delete courses.
* Free and paid courses (free courses always have a price of `0`).
* Paginated catalog browsing with case-insensitive search, filters (category, free/paid, price range, minimum rating), and sorting.
* Courses can be grouped into categories that administrators manage.
* Student enrollment and a "my courses" view.

### 3. Lessons

* Administrators can add lessons (title and video URL) to a course.
* Anyone can list a course's lessons; the course's lesson count is tracked automatically.

### 4. Learning Progress

* Enrolled students can mark lessons as complete (or undo it).
* Progress is reported per course as completed/total lessons and a percentage, plus an overview across all enrolled courses.

### 5. Reviews & Ratings

* Logged-in users can review a course with a comment and rating, once per course.
* Each course keeps a running average rating and review count.
* Reviews can be listed per course or per user.

### 6. Interactive API Documentation

* Automatically generated OpenAPI specification.
* Swagger UI with JWT Bearer authentication support for testing in the browser.

---

## API Documentation (Swagger UI)

When the service is running, explore and test the endpoints in the browser:

* **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI Specification JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

> **Testing protected routes in Swagger:**
> 1. Call `POST /api/auth/login` with your credentials to get a token.
> 2. Click the **Authorize** button at the top of the Swagger page.
> 3. Paste the JWT token (without the `Bearer ` prefix) into the `Value` field and submit.

A Postman collection is also included in the `postman/` folder.

---

## API Endpoints Reference

### Authentication (`/api/auth`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Register a new account and get a JWT | Public |
| `POST` | `/api/auth/login` | Log in and get a JWT | Public |
| `POST` | `/api/auth/forgot-password` | Email a password reset token (body: `{"email"}`) | Public |
| `POST` | `/api/auth/reset-password` | Set a new password (body: `{"token", "newPassword"}`) | Public |

### Course Catalog & Learning

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/courses` | List courses (filters and sorting below) | Public |
| `GET` | `/api/courses/{id}` | Get a course's details | Public |
| `GET` | `/api/courses/{courseId}/lessons` | List a course's lessons | Public |
| `POST` | `/api/courses/{id}/enroll` | Enroll the current user in a course | Authenticated |
| `GET` | `/api/my-courses` | List the current user's enrolled courses | Authenticated |
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
| `sortBy` | `sortBy=price` | `createdAt` (default), `title`, `price`, `averageRating`, or `reviewCount` |
| `direction` | `direction=asc` | `asc` or `desc` (default) |

### Profile (`/api/profile`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/profile` | Get the current user (`id`, `name`, `email`, `role`) | Authenticated |
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
| `GET` | `/api/courses/{courseId}/reviews` | List a course's reviews | Public |
| `POST` | `/api/courses/{courseId}/reviews` | Review a course (body: `{"comment", "rating"}`) | Authenticated |
| `GET` | `/api/users/{userId}/reviews` | List a user's reviews | Authenticated |

### Administration (`/api/admin`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/admin/courses` | Create a course (set a category with `"category": {"id": 1}`) | Admin |
| `PUT` | `/api/admin/courses/{id}` | Update a course | Admin |
| `DELETE` | `/api/admin/courses/{id}` | Delete a course | Admin |
| `POST` | `/api/admin/courses/{courseId}/lessons` | Add a lesson to a course | Admin |
| `GET` | `/api/admin/users` | List all users (passwords never returned) | Admin |
| `POST` | `/api/admin/categories` | Create a category | Admin |
| `PUT` | `/api/admin/categories/{id}` | Rename or describe a category | Admin |
| `DELETE` | `/api/admin/categories/{id}` | Delete a category (only if no courses use it) | Admin |

### System

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/health` | Check that the service is running | Public |

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
```

Generate a JWT secret (a base64-encoded 256-bit key) with:

```bash
openssl rand -base64 32
```

`.env` is ignored by git. The app reads it automatically when run locally, and Docker Compose passes it into the container.

### Option A: Run with Docker (recommended)

Prerequisites: JDK 25 and Docker.

```bash
./mvnw package -DskipTests
docker build -t minhaj-backend .
docker compose up
```

This starts:

* **PostgreSQL** (`minhaj-db`), reachable from your machine on port `5433`.
* **The API** (`minhaj-app`) on [http://localhost:8080](http://localhost:8080), started once the database is healthy.

Rebuild the jar and image after code changes. To wipe the database and start fresh, run `docker compose down -v`.

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

---

## Roadmap

* [ ] AWS S3 integration for course images and lesson media.
* [ ] Administrative analytics dashboard endpoints.
