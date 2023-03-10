FROM openjdk:11

WORKDIR /usr/src/

ARG JAR_FILE

RUN mkdir -p /usr/src/{logs,downloading}

COPY ${JAR_FILE} .

CMD ["java","-jar","jacodb-http-demo.jar"]