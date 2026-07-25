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

Without `--db-prod`, the backend launcher overrides the production datasource
defaults with an in-memory H2 database.

Plain development mode uses an in-memory Firebase implementation initialized
from `backend/devel/firebase.json`. The `--db-prod` flag uses both the production
SQL database and real Firebase.

Build and deploy the backend and frontend containers:

```sh
./build_deploy.sh
```

Image names, container names, ports, backend URLs, and deployment hosts can be
overridden through the environment variables supported by each module's
`build_deploy.sh`.
