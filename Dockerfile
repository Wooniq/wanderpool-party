FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8083 9091

ENV JAVA_OPTS=""
ENV PARTY_SERVER_PORT=8083

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
