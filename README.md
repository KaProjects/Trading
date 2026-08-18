# Trading

Run the complete local quality suite from this directory:

```sh
./verify.sh
```

Run the backend and frontend in separate terminals:

```sh
(cd backend && ./build_dev.sh)
(cd frontend && ./build_dev.sh)
```

Run development mode against the production database:

```sh
(cd backend && ./build_dev.sh --db-prod)
```

Plain development mode activates the backend Maven `dev` profile and uses H2
with an in-memory Firebase implementation initialized from
`backend/src/dev/resources/firebase.json`. The `--db-prod` flag runs Quarkus
without the Maven `dev` profile, exports the required development HTTP and
production database environment variables, and uses the production SQL
database and real Firebase.
