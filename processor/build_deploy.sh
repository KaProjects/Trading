docker build -t trader-processor:v1.1 .
docker run -d -v $(pwd):/workdir trader-processor:v1.1