# syntax=dockerfile:1
# Imagen única: construye el frontend (React) y el backend (Spring Boot) y
# sirve ambos desde la misma aplicación (API + recursos estáticos).

# ---------- 1) Frontend ----------
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- 2) Backend ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /backend
COPY backend/pom.xml ./
COPY backend/src ./src
# El build de React queda como recursos estáticos dentro del jar
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

# ---------- 3) Runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /backend/target/backend-1.0.0.jar app.jar
RUN groupadd --system app && useradd --system --no-create-home --gid app app \
    && chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
