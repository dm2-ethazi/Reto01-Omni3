FROM eclipse-temurin:17-jdk-jammy
ARG DERBY_VERSION=10.16.1.1
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /opt/derby/lib \
    && for a in derby derbyshared derbynet derbyclient derbytools; do \
         curl -fsSL -o /opt/derby/lib/${a}.jar \
           https://repo1.maven.org/maven2/org/apache/derby/${a}/${DERBY_VERSION}/${a}-${DERBY_VERSION}.jar || exit 1; \
       done
COPY java-servers/common/Seeder.java /opt/derby/Seeder.java
COPY seeds/derby.sql /opt/derby/seed.sql
COPY java-servers/derby/init.sh /opt/derby/init.sh
RUN javac -encoding UTF-8 -d /opt/derby /opt/derby/Seeder.java \
    && chmod +x /opt/derby/init.sh
EXPOSE 1527
CMD ["/opt/derby/init.sh"]
