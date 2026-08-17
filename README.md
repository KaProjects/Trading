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

Build and deploy the JVM backend and frontend containers locally in separate
terminals:

```sh
(cd backend && ./build_deploy.sh prod)
(cd frontend && ./build_deploy.sh prod)
```

Deploy the JVM backend with memory limits suitable for a small server:

```sh
(cd backend && ./build_deploy.sh prod --low-memory)
```

Build a portable Linux AMD64 native image on the development machine and
deploy it directly to the NAS over SSH:

```sh
(cd backend && ./build_deploy.sh prod --nas-native)
```

The NAS deployment verifies the image platform before streaming it through
SSH, loading it into the remote Docker daemon, and recreating the backend
container. It defaults to `Stanley@192.168.1.122`; `NAS_USER` and `NAS_HOST`
can override the target. The existing container's Firebase credential mount is
reused. Set `NAS_FIREBASE_CREDENTIALS_PATH` to its path on the NAS for the
first deployment or when the location changes. NAS native deployments always
apply the low-memory runtime limits described below.

The low-memory profile limits the backend container to `512m`, caps the
application heap at `128m`, reduces the Quarkus database and worker pools, and
uses more eager JVM heap/native-memory trimming. `BACKEND_MEMORY_LIMIT`,
`BACKEND_HEAP_MIN`, `BACKEND_HEAP_MAX`, `BACKEND_HEAP_MIN_FREE_RATIO`,
`BACKEND_HEAP_MAX_FREE_RATIO`, and `BACKEND_NATIVE_HEAP_TRIM_INTERVAL` can
override the default limits.

The standalone `--low-memory` flag is only needed for a local JVM production
deployment; it does not change native image compilation or image transfer.

Image names, container names, ports, backend URLs, and deployment hosts can be
overridden through the environment variables supported by each module's
`build_deploy.sh`.
