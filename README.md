# Minhaj - Educational Platform API

Minhaj is an enterprise-grade backend RESTful API designed for an educational learning platform. Built with a production-ready architecture, it provides stateless JWT authentication, role-based access control (RBAC), database auditing, robust exception handling, and interactive OpenAPI documentation.

---

## Tech Stack

* **Framework:** Spring Boot 4.1.0
* **Language:** Java 25
* **Database:** PostgreSQL
* **Security:** Spring Security & JWT (JSON Web Tokens)
* **ORM:** Hibernate / Spring Data JPA
* **Validation:** Jakarta Validation (`spring-boot-starter-validation`)
* **API Documentation:** Springdoc OpenAPI / Swagger UI
* **Build Tool:** Maven

---

## Architectural Highlights

* **DTO Pattern with Java Records:** Separation of persistence entities from API response contracts using Java `record` implementations (`CourseDTO`) to prevent data leakage.
* **Centralized Exception Handling:** `@ControllerAdvice` (`GlobalExceptionHandler`) to intercept and format runtime exceptions and validation failures into structured, readable JSON responses.
* **JPA Auditing:** Automated entity lifecycle timestamp tracking (`@CreatedDate`, `@LastModifiedDate`).
* **Pagination & Dynamic Search:** Scalable data delivery utilizing Spring Data's `Pageable` and `Page` to query large datasets efficiently.
* **Cross-Origin Resource Sharing (CORS):** Centralized CORS policy in `SecurityConfig` to facilitate cross-origin communication with frontend and mobile clients.
* **Stateless JWT Security:** Interception filter for Bearer token validation and route authorization.

---

## Features Implemented

### 1. Authentication & Security

* User registration with BCrypt password hashing.
* Stateless login issuing cryptographically signed JWT tokens.
* Role-Based Access Control distinguishing standard students from administrators.
* Fine-grained route security rules with custom `SecurityFilterChain`.

### 2. Course Management

* Full CRUD capabilities for administrators to create, update, and remove courses.
* Paginated catalog browsing with case-insensitive search queries.
* Student enrollment tracking and personal course retrieval.

### 3. Interactive API Documentation

* Automated OpenAPI 3.1 specification generation.
* Secured Swagger UI equipped with JWT Bearer authentication support for browser-based testing.

---

## API Documentation (Swagger UI)

When the service is running, explore and test the endpoints directly in the browser:

* **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI Specification JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

> **Testing Protected Routes in Swagger:**
> 1. Execute `POST /api/auth/login` with your credentials to obtain a token.
> 2. Click the **Authorize** button at the top of the Swagger interface.
> 3. Paste the JWT token (without prefix) into the `Value` field and submit.
> 
> 

---

## API Endpoints Reference

### Authentication (`/api/auth`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Register a new user account | Public |
| `POST` | `/api/auth/login` | Authenticate and obtain JWT token | Public |

### Course Catalog & Learning (`/api`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `GET` | `/api/courses` | List courses (supports `page`, `size`, `search`) | Public |
| `GET` | `/api/courses/{id}` | Retrieve specific course details | Public |
| `POST` | `/api/courses/{id}/enroll` | Enroll the authenticated user into a course | Authenticated |
| `GET` | `/api/my-courses` | Retrieve enrolled courses for current user | Authenticated |

### Course Administration (`/api/admin/courses`)

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/admin/courses` | Create a new course | Admin |
| `PUT` | `/api/admin/courses/{id}` | Update existing course attributes | Admin |
| `DELETE` | `/api/admin/courses/{id}` | Remove a course from the platform | Admin |

---

## Getting Started

### Prerequisites

* JDK 25
* Apache Maven 3.9+
* PostgreSQL 16+ running locally on port `5432`

### Setup & Run

1. Clone the repository:
```bash
git clone https://github.com/your-username/Minhaj.git
cd Minhaj

```


2. Configure database credentials in `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/minhaj_db
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update

```


3. Build and launch the application:
```bash
mvn clean spring-boot:run

```



---

## Roadmap

* [ ] AWS S3 Cloud Integration for course assets and media uploads.
* [ ] Advanced Course Reviews & Rating calculations.
* [ ] Administrative analytics dashboard endpoints.
