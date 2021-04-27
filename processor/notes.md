### deploy to production
cd <trader_processor_production_dir>
docker build -t trader:v1 .
docker run -d trader:v1 -v $(pwd):/workdir