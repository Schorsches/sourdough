FROM maven:3-eclipse-temurin-22-alpine
WORKDIR /tiles
COPY pom.xml pom.xml
RUN mvn dependency:go-offline -B
COPY src src
# Tests are compiled here, so a test that does not build still fails the image, but they
# are not run: surefire resolves its JUnit provider lazily at execution time, and
# `dependency:go-offline` above cannot know to pre-fetch it, so running tests offline
# fails on a missing surefire-junit-platform jar. The test suite runs in CI instead.
RUN mvn package -o -DskipTests
ENTRYPOINT ["java", "-jar", "/tiles/target/sourdough-builder-HEAD-with-deps.jar"]
