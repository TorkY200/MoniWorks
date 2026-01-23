## Build & Run

- Build: `./mvnw compile`
- Run: `./mvnw spring-boot:run`
- Package: `./mvnw package -Pproduction`

## Validation

Run these after implementing to get immediate feedback:

- Tests: `./mvnw test`
- Tests + Coverage: `./mvnw verify` (includes JaCoCo coverage check)
- Formatting check: `./mvnw spotless:check`
- Formatting fix: `./mvnw spotless:apply`
- Forbidden markers: `./scripts/check-forbidden-markers.sh`
- Full release check: `./scripts/release-check.sh` (comprehensive pre-release validation)
- OWASP scan: `./mvnw dependency-check:check` (slow, scans for vulnerabilities)

## Operational Notes

- mvnw needs `chmod +x mvnw` after fresh clone
- H2 console available at /h2-console in dev mode
- Default port: 8080
- Coverage reports at `target/site/jacoco/index.html` after `./mvnw test`

### Codebase Patterns

- Entities in `domain/` package
- Repositories in `repository/` package
- Services in `service/` package
- Vaadin views in `ui/` package
- Flyway migrations in `src/main/resources/db/migration/`
