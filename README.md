# Minhaj - Educational Platform API

Minhaj is a robust backend REST API built for an educational platform. It provides secure user authentication, role-based access control, and robust data management.

## Tech Stack
* **Framework:** Spring Boot 3
* **Language:** Java 25
* **Database:** PostgreSQL
* **Security:** Spring Security & JWT (JSON Web Tokens)
* **ORM:** Hibernate / Spring Data JPA
* **Validation:** Spring Boot Validation

## Features Implemented So Far
* **User Authentication:**
  * Secure user registration with BCrypt password hashing.
  * Login mechanism with JWT generation for stateless authentication.
  * Input validation to ensure data integrity before database insertion.
* **Role-Based Access Control (RBAC):**
  * `STUDENT`: Default role for newly registered users.
  * `ADMIN`: Elevated privileges (Configuration setup in progress).
* **Security Filter Chain:**
  * Custom JWT Request Filter to intercept and validate tokens on protected routes.
  * Public endpoints for registration and login.

## Getting Started

### Prerequisites
* JDK 25
* Maven
* PostgreSQL running locally on port 5432

### Installation & Setup
1. Clone the repository:
   git clone [https://github.com/your-username/Minhaj.git](https://github.com/your-username/Minhaj.git)
   
2. Configure the database in `src/main/resources/application.properties`:
   spring.datasource.url=jdbc:postgresql://localhost:5432/minhaj_db
   spring.datasource.username=your_db_username
   spring.datasource.password=your_db_password
   spring.jpa.hibernate.ddl-auto=update

4. Run the application:
  ./mvnw spring-boot:run

## API Endpoints

| HTTP Method | Endpoint | Description | Access |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Register a new user | Public |
| `POST` | `/api/auth/login` | Login and receive JWT | Public |
| `GET` | `/api/courses/**` | View available courses | Public (WIP) |
