FROM eclipse-temurin:17-jdk-alpine
RUN apk add curl
VOLUME /tmp
ENV JAVA_OPTS=""
EXPOSE 8082
ADD target/springboot-aws-deploy-recipe-server.jar springboot-aws-deploy-recipe-server.jar
ENTRYPOINT ["java","-jar","/springboot-aws-deploy-recipe-server.jar"]