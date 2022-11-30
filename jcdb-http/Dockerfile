FROM openjdk:11

WORKDIR /usr/src/

ARG JAR_FILE

RUN apt-get update && \
    apt-get install -y docker.io \
                       jq && \
    apt-get clean && \
    mkdir -p /usr/src/{logs,downloading}

COPY ${JAR_FILE} .

ENTRYPOINT ["java","-jar","jcdb-http-demo.jar", "--api-prefix=/jcdb-demo" ]