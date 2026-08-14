# Pipeline probe until Spring Boot exists.
# Replace the body of this file with a multi-stage Java build; keep EXPOSE and the smoke port.
FROM python:3.12-alpine
WORKDIR /app
COPY probe/index.html /app/index.html
EXPOSE 8080
CMD ["python", "-m", "http.server", "8080", "--bind", "0.0.0.0"]
