# 标准 caddy 镜像不含 DNS 插件，这里用 xcaddy 构建带 DNS-01 能力的版本。
# DNS_PLUGIN 由 docker-compose 传入：cloudflare / tencentcloud / alidns
ARG CADDY_VERSION=2.8.4

FROM caddy:${CADDY_VERSION}-builder AS builder
ARG DNS_PLUGIN=cloudflare
RUN xcaddy build --with github.com/caddy-dns/${DNS_PLUGIN}

FROM caddy:${CADDY_VERSION}
COPY --from=builder /usr/bin/caddy /usr/bin/caddy
