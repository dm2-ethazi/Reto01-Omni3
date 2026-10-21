FROM gvenzl/oracle-free:23-slim
USER root
COPY seeds/oracle.sql /opt/oracle/r01_seed.sql
COPY oracle/001_seed.sh /container-entrypoint-initdb.d/001_seed.sh
RUN chmod 755 /container-entrypoint-initdb.d/001_seed.sh \
    && chmod 644 /opt/oracle/r01_seed.sql
USER oracle
