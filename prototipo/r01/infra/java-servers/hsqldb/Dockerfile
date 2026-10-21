FROM eclipse-temurin:17-jdk-jammy
ARG HSQLDB_VERSION=2.7.3
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /opt/hsqldb/lib \
    && curl -fsSL -o /opt/hsqldb/lib/hsqldb.jar \
         https://repo1.maven.org/maven2/org/hsqldb/hsqldb/${HSQLDB_VERSION}/hsqldb-${HSQLDB_VERSION}.jar
COPY java-servers/common/Seeder.java /opt/hsqldb/Seeder.java
COPY seeds/hsqldb.sql /opt/hsqldb/seed.sql
COPY java-servers/hsqldb/init.sh /opt/hsqldb/init.sh
RUN javac -encoding UTF-8 -d /opt/hsqldb /opt/hsqldb/Seeder.java \
    && chmod +x /opt/hsqldb/init.sh
EXPOSE 9001
CMD ["/opt/hsqldb/init.sh"]
