# Trading

Run the complete local quality suite from this directory:

```sh
./verify.sh
```

Run the backend and frontend in development mode:

```sh
./build_deploy.sh dev
```

Build and deploy the backend and frontend containers:

```sh
./build_deploy.sh
```

Image names, container names, ports, backend URLs, and deployment hosts can be
overridden through the environment variables supported by each module's
`build_deploy.sh`.
