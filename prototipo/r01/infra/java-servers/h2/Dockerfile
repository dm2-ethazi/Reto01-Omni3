FROM eclipse-temurin:17-jdk-jammy
ARG H2_VERSION=2.2.224
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /opt/h2/lib \
    && curl -fsSL -o /opt/h2/lib/h2.jar \
         https://repo1.maven.org/maven2/com/h2database/h2/${H2_VERSION}/h2-${H2_VERSION}.jar
COPY java-servers/common/Seeder.java /opt/h2/Seeder.java
COPY seeds/h2.sql /opt/h2/seed.sql
COPY java-servers/h2/init.sh /opt/h2/init.sh
RUN javac -encoding UTF-8 -d /opt/h2 /opt/h2/Seeder.java \
    && chmod +x /opt/h2/init.sh
EXPOSE 9092
CMD ["/opt/h2/init.sh"]
