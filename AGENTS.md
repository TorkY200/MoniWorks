## Build & Run

- Build: `./mvnw compile`
- Run: `./mvnw spring-boot:run`
- Package: `./mvnw package -Pproduction`

## Validation

Run these after implementing to get immediate feedback:

- Tests: `./mvnw test`
- Typecheck: `./mvnw compile` (Java compiler catches type errors)
- Lint: Built into compiler

## Operational Notes

- mvnw needs `chmod +x mvnw` after fresh clone
- H2 console available at /h2-console in dev mode
- Default port: 8080

### Codebase Patterns

- Entities in `domain/` package
- Repositories in `repository/` package
- Services in `service/` package
- Vaadin views in `ui/` package
- Flyway migrations in `src/main/resources/db/migration/`
