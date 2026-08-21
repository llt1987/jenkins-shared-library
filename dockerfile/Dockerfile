# Base image: Oracle Linux 8
FROM oraclelinux:8

# Enable Oracle Instant Client repository
RUN dnf install -y oracle-release-el8 \
    && dnf config-manager --enable ol8_oracle_instantclient

# Install Oracle 19c Instant Client (Administrator mode)
RUN dnf install -y \
    oracle-instantclient19.27-basic \
    oracle-instantclient19.27-sqlplus \
    oracle-instantclient19.27-tools \
    oracle-instantclient19.27-devel \
    oracle-instantclient19.27-jdbc \
    oracle-instantclient19.27-odbc \
    && dnf clean all


# Set environment variables
ENV LD_LIBRARY_PATH=/usr/lib/oracle/19.27/client64/lib
ENV PATH=/usr/lib/oracle/19.27/client64/bin:$PATH

# Working directory
WORKDIR /work

# Default command: check SQL*Plus version
CMD ["sqlplus", "-V"]

