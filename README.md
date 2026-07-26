# Trading

Run the complete local quality suite from this directory:

```sh
./verify.sh
```

Run the backend and frontend in development mode:

```sh
./build_deploy.sh dev
```

Run development mode against the production database:

```sh
./build_deploy.sh dev --db-prod
```

Plain development mode activates the backend Maven `dev` profile and uses H2
with an in-memory Firebase implementation initialized from
`backend/src/dev/resources/firebase.json`. The `--db-prod` flag runs Quarkus
without the Maven `dev` profile, exports the required development HTTP and
production database environment variables, and uses the production SQL
database and real Firebase.

Build and deploy the backend and frontend containers:

```sh
./build_deploy.sh
```

Deploy the JVM backend with memory limits suitable for a small server:

```sh
./build_deploy.sh --low-memory
```

The same runtime limits can be applied to a native backend:

```sh
./build_deploy.sh --native --low-memory
```

The low-memory profile limits the backend container to `512m`, caps the
application heap at `128m`, and reduces the Quarkus database and worker pools.
`BACKEND_MEMORY_LIMIT`, `BACKEND_HEAP_MIN`, and `BACKEND_HEAP_MAX` can override
the default limits.

Image names, container names, ports, backend URLs, and deployment hosts can be
overridden through the environment variables supported by each module's
`build_deploy.sh`.
