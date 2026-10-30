# Backend scaffold (Byte)

```bash
mkdir -p backend && cd backend
curl -fsSL https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d javaVersion=21 \
  -d groupId=com.antech -d artifactId=__SLUG__ -d version=1.0.0 \
  -d name=__SLUG__ -d packageName=__PACKAGE__ \
  -d dependencies=web,data-jpa,validation,security,actuator,h2,postgresql \
  -o ../backend.zip
unzip -o ../backend.zip -d . && rm ../backend.zip
```
- Leave `bootVersion` unset to get the current Initializr default. Use `javaVersion` = the installed LTS (17, 21 or 25).
- Add springdoc to `pom.xml`, choosing the major line that matches Spring Boot
  (springdoc 2.x for Boot 3.x, springdoc 3.x for Boot 4.x; use its latest release):
```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>MATCHING_VERSION</version>
</dependency>
```
- Make the JAR name predictable: inside `<build>` add `<finalName>${project.artifactId}-${project.version}</finalName>`.
- Replace `src/main/resources/application.properties` with `application.yml` from this folder.
- Copy `SecurityConfig.java` -> `config/`, `GlobalExceptionHandler.java` -> `web/`, exceptions from
  `Exceptions.java.txt` -> `service/`. Replace `__PACKAGE__` and `__SLUG__`.

## Package layout
```
__PACKAGE__
  config/      SecurityConfig, OpenApiConfig (title, version, description)
  web/         *Controller, GlobalExceptionHandler
  web/dto/     *Request, *Response records with Bean Validation
  service/     *Service, NotFoundException, ConflictException
  domain/      JPA entities (UUID id, @Version version, createdAt/updatedAt via @PrePersist/@PreUpdate)
  repository/  Spring Data JPA interfaces
```
Use Java `record`s for DTOs and a small mapper (static `from(entity)` on the response record).
