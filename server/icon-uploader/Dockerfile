FROM golang:1.23-alpine AS builder
WORKDIR /src
COPY main.go .
RUN go build -o /icon-uploader main.go

FROM alpine:3.20
RUN adduser -D -u 10001 app
USER app
COPY --from=builder /icon-uploader /usr/local/bin/icon-uploader
EXPOSE 8080
CMD ["icon-uploader"]
