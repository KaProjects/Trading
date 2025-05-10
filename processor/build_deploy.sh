
docker build -t trader-processor:v1.1 . \
    || { printf '\e[31mERROR: docker build failed\n' ; exit 1; }

docker run -d -v $(pwd):/workdir trader-processor:v1.1