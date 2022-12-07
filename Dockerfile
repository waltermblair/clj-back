FROM clojure:temurin-19-lein-bullseye-slim AS BUILD

COPY . /code
WORKDIR /code
RUN lein uberjar

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=BUILD /code/target/*-standalone.jar ./app.jar
CMD ["java", "-jar", "app.jar"]