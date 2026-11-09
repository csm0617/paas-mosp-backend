# arm64 静态二进制，scratch 镜像；不需要 iproute2（网卡信息走 net.Interfaces）
FROM --platform=linux/arm64 golang:1.21-alpine AS build
WORKDIR /src
COPY go.mod main.go ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=arm64 \
    go build -trimpath -ldflags="-s -w" -o /out/vip-daemon .

FROM scratch
COPY --from=build /out/vip-daemon /vip-daemon
EXPOSE 5556
ENTRYPOINT ["/vip-daemon"]
CMD ["-config", "/data/paas/vip/config.json"]
