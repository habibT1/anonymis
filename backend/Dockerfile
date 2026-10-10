FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar \
 && cp "$(ls build/libs/*.jar | grep -v plain | head -1)" /app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
# firebase-key.json est monté par docker-compose dans /app (jamais copié dans l'image).
ENTRYPOINT ["java", "-jar", "app.jar"]
