# syntax=docker/dockerfile:1
# ═══════════════════════════════════════════════════════════════════
# Contenedor del cliente de escritorio Gestor Hogwarts (JavaFX).
#
#   Construcción:  docker build -f gestorhogwartsjavafx/Dockerfile -t gestorhogwarts-javafx gestorhogwartsjavafx
#   Ejecución:     docker run --rm gestorhogwarts-javafx
#
# La ventana se pinta en un X virtual (Xvfb), así el contenedor no
# necesita servidor gráfico del host. Para ver la interfaz en el host
# hay que montar un X11 (o usar X410/VcXsrv); el objetivo del
# contenedor es despliegue reproducible, no sustituir al JAR nativo.
# ═══════════════════════════════════════════════════════════════════

# ─── Fase 1: compilación con Maven ────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS construccion
WORKDIR /app

COPY pom.xml .
# Calienta la caché de dependencias en una capa propia.
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -q clean package

# ─── Fase 2: ejecución con X virtual ─────────────────────────────
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        xvfb xauth \
        libgtk-3-0 libgl1 libglu1-mesa libxtst6 \
        libx11-6 libxext6 libxi6 libxrender1 libxxf86vm1 libxrandr2 \
        fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=construccion /app/target/gestorhogwartsjavafx-1.0.0.jar ./gestorhogwartsjavafx.jar

# Sin backend la app arranca igual (la pantalla de salud muestra el
# error sin cerrarse): el arranque es independiente de la API.
CMD ["xvfb-run", "-a", "--server-args=-screen 0 1280x800x24", \
     "java", "-jar", "gestorhogwartsjavafx.jar"]
