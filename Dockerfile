FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/exampletelegrambot-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "app.jar"]
ENTRYPOINT ["java", "-jar", "app.jar", \
            "--telegram.bot.name=unnode002_bot", \
            "--telegram.bot.token=8186231076:AAH7DNomL3e7QQC_g1wc3ngY0SNA2bXh4NY", \
            "--telegram.review.group.id=-4894905748", \
            "--telegram.public.group.id=-1002279220106" \
           ]