FROM openjdk:11
ARG JAR_FILE=build/libs/jcdb-http-demo.jar

RUN apt-get update && \
    apt-get install -y docker.io \
                       jq && \
    apt-get clean && \
    mkdir -p /var/jcdb-demo/{logs,downloading}

COPY ${JAR_FILE} jcdb-http-demo.jar
ENTRYPOINT ["java","-jar","/jcdb-http-demo.jar", "--api-prefix=/jcdb-demo" ]