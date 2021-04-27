docker build -t trader:v1 .
docker run -d -v $(pwd):/workdir trader:v1