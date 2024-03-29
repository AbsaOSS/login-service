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
ARG BASE_IMAGE=tomcat:9-jdk11-corretto

# --- Base image part (common for SSL true|false) ---
FROM $BASE_IMAGE as base

# Provide your proxy if needed, e.g. http://my.proxy.examle.com:3128
ARG BUILD_PROXY

# Provide path to the directory with LDAP certs in PEM format
ARG LDAP_SSL_CERTS_PATH
# ARG SSL_DNAME is defined below in the SSL-enabled image
# In case you build the Dockerfile from another location than the default 'service' dir, provide a prefix to reach it
ARG LS_PREFIX=.

LABEL org.opencontainers.image.authors="ABSA"

# deploy as root application in tomcat
COPY ${LS_PREFIX}/target/scala-2.12/*.war /usr/local/tomcat/webapps/ROOT.war

# "/opt/config" dir needs to externally mounted (and must contain application.yaml) when run
# e.g. docker run -p 8080:8080 -p 8443:8443 -v "$(pwd)/src/main/resources:/opt/config" absaoss/login-service:latest
ENV SPRING_CONFIG_LOCATION=/opt/config/application.yaml

ENV http_proxy=$BUILD_PROXY
ENV https_proxy=$BUILD_PROXY
ENV HTTP_PROXY=$BUILD_PROXY
ENV HTTPS_PROXY=$BUILD_PROXY


RUN mkdir -p /opt/certs
# just PEMs are supported
COPY $LDAP_SSL_CERTS_PATH/*.pem /opt/certs/

# Java 11 paths used
RUN for file in `ls /opt/certs/*.pem`; \
do \
    echo yes | keytool -import -file $file -alias ldaps$RANDOM -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit; \
done

# uncomment and add packages you would need to install via yum if any
#RUN yum -y update
#RUN yum -y install htop procps

# --- SSL=true image specifics ---
FROM base AS base-ssl-true
ENV SSL_ENABLED=true
RUN echo "This stage sets SSL=$SSL_ENABLED"
# Enable SSL: Add our SSL connector after <Service name="Catalina"> is found in server.xml; .bak = backup
RUN sed -i.bak '/<Service name="Catalina">/a <Connector port="8443" protocol="HTTP/1.1" SSLEnabled="true" maxThreads="150" scheme="https" secure="true" keystoreFile="/usr/local/tomcat/conf/selfsigned.p12" keystorePass="changeit" clientAuth="false" acceptCount="100"/>' /usr/local/tomcat/conf/server.xml

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
