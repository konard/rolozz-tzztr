FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY build/libs/CollectorService-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-XX:+UseShenandoahGC", "-jar", "app.jar"]