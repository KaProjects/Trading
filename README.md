# Trading

Run the complete local quality suite from this directory:

```sh
./verify.sh
```

Run the backend and frontend in separate terminals:

```sh
(cd backend && ./build_deploy.sh dev)
(cd frontend && ./build_deploy.sh dev)
```

Run development mode against the production database:

```sh
(cd backend && ./build_deploy.sh dev --db-prod)
```

Plain development mode activates the backend Maven `dev` profile and uses H2
with an in-memory Firebase implementation initialized from
`backend/src/dev/resources/firebase.json`. The `--db-prod` flag runs Quarkus
without the Maven `dev` profile, exports the required development HTTP and
production database environment variables, and uses the production SQL
database and real Firebase.

## Production commands

| Command | Description |
| --- | --- |
| `(cd backend && ./build_deploy.sh prod)` | Build the JVM backend image and recreate the local container. |
| `(cd frontend && ./build_deploy.sh prod)` | Build the frontend image and recreate the local container. |
| `(cd backend && ./build_deploy.sh prod --push-nas)` | Build the Linux AMD64 JVM image and deploy it to the NAS. |
| `(cd frontend && ./build_deploy.sh prod --push-nas)` | Build the Linux AMD64 frontend image and deploy it to the NAS. |
| `(cd backend && ./build_deploy.sh prod --low-memory)` | Run the local JVM backend with the low-memory profile. |
| `(cd backend && ./build_deploy.sh prod --push-nas --low-memory)` | Deploy the JVM backend to the NAS with the low-memory profile. |
| `(cd backend && ./build_deploy.sh prod --nas-native)` | Build and deploy the Linux AMD64 native backend; NAS push and low-memory settings are automatic. |

The `--push-nas` deployment verifies the image platform before streaming it
through SSH, loading it into the remote Docker daemon, and recreating the
corresponding container. It defaults to `Stanley@192.168.1.122`; `NAS_USER`
and `NAS_HOST` can override the target. The backend reuses the existing
container's Firebase credential mount. Set `NAS_FIREBASE_CREDENTIALS_PATH` to
its path on the NAS for the first backend deployment or when the location
changes. `--nas-native` implies `--push-nas` and always applies the low-memory
runtime limits described below.

The low-memory profile limits the backend container to `512m`, caps the
application heap at `128m`, reduces the Quarkus database and worker pools, and
uses more eager JVM heap/native-memory trimming. `BACKEND_MEMORY_LIMIT`,
`BACKEND_HEAP_MIN`, `BACKEND_HEAP_MAX`, `BACKEND_HEAP_MIN_FREE_RATIO`,
`BACKEND_HEAP_MAX_FREE_RATIO`, and `BACKEND_NATIVE_HEAP_TRIM_INTERVAL` can
override the default limits.

The standalone `--low-memory` flag can be used for a local JVM production
deployment or combined with `--push-nas` for a JVM deployment to the NAS. It
does not change image compilation or transfer.

Image names, container names, ports, backend URLs, and deployment hosts can be
overridden through the environment variables supported by each module's
`build_deploy.sh`.
