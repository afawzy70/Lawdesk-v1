# syntax=docker/dockerfile:1
# LawDesk backend image (LD-006). Tests run in CI before this build, so packaging skips them.
FROM eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24@sha256:fd52084c35bbac78890d95d57707d31ffc9d7a3324199e89c06be2a9dc71bfa0 AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -q -DskipTests package \
    && cp target/lawdesk-backend-*.jar /app.jar

FROM eclipse-temurin:21.0.12.1_1-jre-alpine-3.24@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
# Text recognition (LD-DOC-13-004, owner decision D7): Tesseract 5.5 with the Arabic and English data, from the same pinned Alpine
# release as the base image (the base is pinned by digest; the packages are held to the 5.5 series). OcrEngine finds `tesseract` on
# PATH and the data in /usr/share/tessdata; the CI image job runs `tesseract --list-langs` on this image. OMP_THREAD_LIMIT=1 keeps
# one recognition on one core (the page worker is single-threaded; OpenMP threads only add contention on a shared host).
RUN apk add --no-cache 'tesseract-ocr~=5.5' 'tesseract-ocr-data-ara~=5.5' 'tesseract-ocr-data-eng~=5.5' \
    && tesseract --list-langs
ENV OMP_THREAD_LIMIT=1
RUN addgroup -S -g 10001 lawdesk && adduser -S -u 10001 -G lawdesk -H lawdesk
COPY --from=build /app.jar /opt/lawdesk/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "-jar", "/opt/lawdesk/app.jar"]
