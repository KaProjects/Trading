FROM registry.access.redhat.com/ubi9/openjdk-25:1.24 AS build

USER 0
WORKDIR /workspace
RUN chown 185:0 /workspace

COPY --chown=185:0 .mvn/ .mvn/
COPY --chown=185:0 mvnw pom.xml ./

USER 185
RUN ./mvnw -B dependency:go-offline

COPY --chown=185:0 src/ src/
COPY --chown=185:0 sql/ sql/
RUN ./mvnw -B clean package

FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
