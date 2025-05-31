FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/exampletelegrambot-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 7860
ENTRYPOINT ["java", "-jar", "app.jar"]