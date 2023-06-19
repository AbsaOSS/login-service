# LS docker file

# Tomcat OS base image
FROM artifacts.bcp.absa.co.za/docker.io/tomcat:9.0.62-jdk8-corretto

LABEL org.opencontainers.image.authors="ABSA OSS"
LABEL org.opencontainers.image.title="Login service"
LABEL org.opencontainers.image.base.name="absaoss/login-service:latest"

ARG BUILD_PROXY
ARG CONFIG=./src/main/resources/example.application.yaml

ENV LS_WAR_PATH=./target/scala-2.12
# deploy as root
ENV LS_WAR=ROOT.war

# build via (docker root = project root):
# docker build -t absaoss/login-service:latest --build-arg BUILD_PROXY=http://my.cool.proxy.here:3128 --build-arg CONFIG=./src/main/resources/my.awesome.local.application.yaml .
# run via
# docker run -p 8080:8080 absaoss/login-service:latest

# test via:
# http://localhost:8080/token/public-key


# Copy Spring application properties
COPY $CONFIG /opt/application.yaml

# Copy Tomcat properties # todo needed for what?
#COPY ./deployment/npintdedatassets/dev/resources/tomcat/server.xml /usr/local/tomcat/conf/
#COPY ./deployment/npintdedatassets/dev/resources/tomcat/rewrite.config /usr/local/tomcat/conf/Catalina/localhost/

# Copy applications (wildcards!)
COPY $LS_WAR_PATH/*.war /usr/local/tomcat/webapps/$LS_WAR

ENV SPRING_CONFIG_ADDITIONAL-LOCATION=/opt/application.yaml

ENV http_proxy=$BUILD_PROXY
ENV https_proxy=$BUILD_PROXY
ENV HTTP_PROXY=$BUILD_PROXY
ENV HTTPS_PROXY=$BUILD_PROXY

#RUN yum -y update
#RUN yum -y install htop procps

EXPOSE 8080

CMD ["catalina.sh", "run"]

