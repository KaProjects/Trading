# Container Memory Statistics

A small, dependency-free Go service that periodically reads container memory
usage from the Docker Engine API. It keeps only streaming aggregates in memory:

- current, minimum, maximum, and average memory usage
- successful observation count
- observation counts and percentages in 10 MiB histogram buckets

Raw observations are not persisted. Completed measurement periods are appended
to `data/history.csv`.

## Deploy

Use the encrypted deployment script to build the image, replace the existing
container, and follow its logs:

```sh
./build_deploy.sh
```

The monitor starts with itself as the only target when no container
configuration exists. It takes the first sample immediately and uses a
one-minute interval by default.

The deployment script provides the published HTTP port. Alternatively, set
`PORT` explicitly before using Docker Compose:

```sh
export PORT="<published-port>"
export MEM_STATS_CONTAINER_NAME="<monitor-container-name>"
docker compose up -d --build
```

Open `http://localhost:${PORT}`.

## Manage containers

Add and remove containers from the HTML report, or call the endpoints directly:

```sh
curl -X POST "http://localhost:${PORT}/add/${TARGET_CONTAINER}"
curl -X DELETE "http://localhost:${PORT}/del/${TARGET_CONTAINER}"
```

The monitored container names are persisted in `data/containers.json`. The
service creates this file automatically. It can also be edited manually while
the service is stopped:

```json
{
  "containers": [
    "container-name"
  ]
}
```

Mount the entire `/data` directory rather than the individual file because
updates replace the file atomically. Use stable container names rather than IDs
so monitoring resumes after a container is recreated with the same name.

Removing a container archives its current statistics before deleting it from
the active list. An archive is also written when the monitor detects that a
target exited, disappeared, restarted, or was recreated. A normal monitor
shutdown archives all active measurement periods. Each CSV row contains the
container identity, observation time range, completion reason, sample count,
current/minimum/maximum/average MiB, and histogram counts and percentages.

An abrupt monitor termination such as `SIGKILL`, an out-of-memory kill, or host
power loss cannot write a final archive because the process is no longer
running.

## Configuration

| Environment variable | Default | Description |
| --- | --- | --- |
| `HTTP_ADDRESS` | `:8080` | HTTP report listen address |
| `DOCKER_SOCKET` | `/var/run/docker.sock` | Docker Engine Unix socket |
| `CONFIG_PATH` | `/data/containers.json` | Writable container configuration |
| `HISTORY_PATH` | `/data/history.csv` | Completed measurement periods |
| `SELF_CONTAINER_NAME` | required | Initial target when config is absent |
| `SAMPLE_INTERVAL` | `1m` | Delay between sampling rounds |
| `DOCKER_REQUEST_TIMEOUT` | `10s` | Timeout for one Docker request |
| `BUCKET_SIZE_MIB` | `10` | Histogram bucket width in MiB |

For load testing, use a shorter `SAMPLE_INTERVAL` to reduce the chance of
missing brief memory spikes.

## Security

The Docker socket grants highly privileged access to the Docker daemon. The
service only calls container inspection and statistics endpoints, but it should
still be treated as trusted code and its HTTP port should only be exposed to a
trusted network. The `:ro` bind option protects the socket file itself; it does
not restrict which Docker API operations a process with socket access could
request.
