FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
RUN addgroup --system wallet && adduser --system --ingroup wallet wallet
COPY build/libs/wallet-transfer-system-*.jar app.jar
USER wallet
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
