# trader-researcher

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./build_deploy.sh dev
```

Development output is kept in `target-dev` so tests and production builds can
use `target` without stopping the live development process.

Standard dev mode uses H2 plus fake Firebase, Polygon, and Finnhub clients.
Polygon and Finnhub data is maintained in `src/dev/resources/polygon.json` and
`src/dev/resources/finnhub.json`. The local Firebase snapshot remains in
`src/dev/resources/firebase.json` and is downloaded once when missing.

Running `./build_deploy.sh dev --db-prod` intentionally uses the production
database and real external clients instead.

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/trader-researcher-1.0.0-SNAPSHOT-runner`

To build a Linux AMD64 native container on an ARM Mac and deploy it to the NAS
without an image registry, run:

```shell script
./build_deploy.sh prod --nas-native
```

The build uses Docker Buildx with `linux/amd64`, verifies the resulting image,
and streams it to `Stanley@192.168.1.122` through SSH. It then recreates the
remote container with the NAS low-memory limits automatically. The SSH client
prompts for a password when key-based authentication is not configured.
Override the target using `NAS_USER` and `NAS_HOST`. On the first deployment, set
`NAS_FIREBASE_CREDENTIALS_PATH` to the Firebase credential file's absolute path
on the NAS; later deployments reuse the existing container's mount.

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- RESTEasy Reactive ([guide](https://quarkus.io/guides/resteasy-reactive)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.

## Provided Code

### RESTEasy Reactive

Easily start your Reactive RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
