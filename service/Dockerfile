# LS docker file

# build via (docker root = project root):
# docker build -t absaoss/login-service:latest \
# --build-arg BUILD_PROXY=http://my.cool.proxy.here:3128 \
# --build-arg CONFIG=./path/to/my.awesome.local.application.yaml \
# --build-arg SSL=true \
# --build-arg SSL_DNAME="CN=*.my.domain.com, OU=project1, O=mycorp, L=Johannesburg, ST=Gauteng, C=za" .
# run via
# docker run -p 8080:8080 -p 8443:8443 absaoss/login-service:latest

# test via:
# http://localhost:8080/token/public-key
# https://localhost:8443/token/public-key

# Conditional Docker image creation idea: https://stackoverflow.com/a/60820156/1773349
# change to OFF to disable SSL
ARG SSL=true
# Tomcat OS base image, feel free to use another (e.g. your hardened one)
ARG BASE_IMAGE=tomcat:9-jdk8-corretto

# --- Base image part (common for SSL true|false) ---
FROM $BASE_IMAGE as base

# Provide your proxy if needed, e.g. http://my.proxy.examle.com:3128
ARG BUILD_PROXY
# Override of the example application config is possible
ARG CONFIG=./src/main/resources/example.application.yaml
# Provide path to LDAP cert in PEM format
ARG LDAP_SSL_CERT

# ARG SSL_DNAME is defined below in the SSL-enabled image

LABEL org.opencontainers.image.authors="ABSA"

ENV LS_WAR_PATH=./target/scala-2.12
# deploy as root
ENV LS_WAR=ROOT.war

# Copy Spring application properties
COPY $CONFIG /opt/application.yaml

COPY $LS_WAR_PATH/*.war /usr/local/tomcat/webapps/$LS_WAR

ENV SPRING_CONFIG_ADDITIONAL-LOCATION=/opt/application.yaml

ENV http_proxy=$BUILD_PROXY
ENV https_proxy=$BUILD_PROXY
ENV HTTP_PROXY=$BUILD_PROXY
ENV HTTPS_PROXY=$BUILD_PROXY

# LDAP cert setup - include cert in cacerts to be trusted (JDK8 path version)
COPY $LDAP_SSL_CERT /opt/ldapcert.pem
RUN echo yes | keytool -import -file /opt/ldapcert.pem -alias ldaps -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit

# uncomment and add packages you would need to install via yum if any
#RUN yum -y update
#RUN yum -y install htop procps

# --- SSL=true image specifics ---
FROM base AS base-ssl-true
ENV SSL_ENABLED=true
RUN echo "This stage sets SSL=$SSL_ENABLED"
# Copy Tomcat properties (to enable SSL)
COPY ./src/main/resources/tomcat-ssl/server.xml /usr/local/tomcat/conf/

# DNAME for self-signed cert, only applied for SSL=true
ARG SSL_DNAME="CN=*.my.example.com, OU=project1, O=yourcompany, L=Johannesburg, ST=Gauteng, C=za"

# A self-seigned certificate for HTTPS
RUN keytool -genkeypair -keyalg RSA -alias tomcat -keysize 2048 \
    -dname "$SSL_DNAME" \
    -validity 365 -storepass changeit -keystore /usr/local/tomcat/conf/selfsigned.p12 -storetype PKCS12

EXPOSE 8080 8443
CMD ["catalina.sh", "run"]

# --- SSL=false image specifics ---
FROM base AS base-ssl-false
ENV SSL_ENABLED=false
RUN echo "This stage sets SSL=$SSL_ENABLED"

EXPOSE 8080
CMD ["catalina.sh", "run"]

# --- Final image assembly ---
FROM base-ssl-${SSL} AS final
RUN echo "Running final stage with SSL_ENABLED=$SSL_ENABLED."
