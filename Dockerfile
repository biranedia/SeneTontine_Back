# Image de production du backend SèneTontine (Render, ou tout hébergeur Docker).
# Étape 1 : compilation avec Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Étape 2 : image d'exécution légère (JRE seul), utilisateur non-root.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
# Réglé pour une instance gratuite de 512 Mo.
ENV JAVA_OPTS="-Xmx320m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
