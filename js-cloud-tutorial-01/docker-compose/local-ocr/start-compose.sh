#!/usr/bin/env bash

set -eu pipefail ;

_configure_container_mounts(){
  echo ;
  mkdir -p ./mariadb-data/ ;
  mkdir -p ./minio-data/ ;

  return 0 ;
} ;

_start_docker_compose_stack(){
  echo ">>>[dc-deploy] start docker-compose stack and follow logs" ;
  echo ">>>[dc-deploy] (You can disconnect from logs with Ctrl+C without stopping containers)" ;
  docker-compose up -d ;
  docker-compose logs -f
  return 0 ;
} ;


_main() {
  _configure_container_mounts ;
  _start_docker_compose_stack ;
  return 0 ;
}

_main ;
