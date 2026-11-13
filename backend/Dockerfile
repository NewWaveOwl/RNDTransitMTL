FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradle/ gradle/
COPY gradlew gradlew.bat ./
COPY backend/build.gradle.kts backend/settings.gradle.kts backend/gradle.properties backend/
COPY backend/src/ backend/src/
RUN chmod +x gradlew && ./gradlew -p backend installDist --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /opt/backend
COPY --from=build /src/backend/build/install/backend/ ./
ENV HOST=0.0.0.0 PORT=18088 FARE_ZONES_FILE=/var/lib/rnd/fare-zones-2026.geojson
EXPOSE 18088
ENTRYPOINT ["/opt/backend/bin/backend"]
