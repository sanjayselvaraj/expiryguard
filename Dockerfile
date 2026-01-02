FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/expiryguard-1.0.0.jar app.jar

EXPOSE 8181

ENV JAVA_OPTS="-Xmx350m -Xms256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]