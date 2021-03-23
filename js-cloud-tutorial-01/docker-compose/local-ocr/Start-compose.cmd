@echo off
set JS_DC_PATH=.
set JS_DOCKER_REGISTRY_PROXY=registry.sol-01.k8s.levigo.net/
set JS_DOCKER_REGISTRY_JADICE=registry.sol-01.k8s.levigo.net/
set MINIO_ENDPOINT=YOUR-IP
set MINIO_PORT=9000
set MINIO_ACCESS_KEY=MINIO-ACCESS-KEY
set MINIO_SECRET_KEY=MINIO-SECRET-KEY

echo Starting docker compose for jadice server with OCR

docker login %JS_DOCKER_REGISTRY_JADICE%

IF NOT EXIST mariadb-data mkdir mariadb-data
IF NOT EXIST minio-data mkdir minio-data

docker-compose up
