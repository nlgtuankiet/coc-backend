FROM openjdk:11-jre-slim
COPY ./build/app.jar ./app.jar
ENTRYPOINT ["java", "-jar","./app.jar"]
