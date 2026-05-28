FROM python:3.12-alpine
RUN adduser -D -u 10001 app
WORKDIR /app
COPY app.py /app/app.py
RUN chmod +x /app/app.py && mkdir -p /data && chown -R app:app /data /app
USER app
EXPOSE 8081
CMD ["python", "/app/app.py"]
