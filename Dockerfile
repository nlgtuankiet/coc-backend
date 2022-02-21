FROM gradle:7.1.1-jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar -i --no-daemon

FROM openjdk:11-jre-slim
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar","/app/app.jar"]
