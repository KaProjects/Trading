FROM golang:1.26-alpine AS build

WORKDIR /src

COPY go.mod ./
COPY *.go ./

RUN CGO_ENABLED=0 GOOS=linux go build \
    -trimpath \
    -ldflags="-s -w" \
    -o /out/mem-stats .

FROM scratch

COPY --from=build /out/mem-stats /mem-stats

EXPOSE 8080
VOLUME ["/data"]

ENTRYPOINT ["/mem-stats"]
