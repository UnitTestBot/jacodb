FROM openjdk:11

WORKDIR /usr/src/

ARG JAR_FILE
ENV API_PREFIX="/utbot-online/jacodb-demo"

RUN mkdir -p /usr/src/{logs,downloading}

COPY ${JAR_FILE} .

CMD ["java","-jar","jacodb-http-demo.jar", "--api-prefix=${API_PREFIX}" ]