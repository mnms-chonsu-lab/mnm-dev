FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# 테스트는 CI(ci.yml)에서 돌린다
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
# 서버 메모리 4GB를 nginx 등과 나눠 쓰므로 힙 상한을 정해 둔다 (제안값, 실측으로 조정)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"
EXPOSE 8080
USER 1000
ENTRYPOINT ["java", "-jar", "app.jar"]
