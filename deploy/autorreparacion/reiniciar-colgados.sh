#!/bin/sh
# Docker reinicia un contenedor cuando su proceso termina (restart: always), pero no cuando solo queda
# unhealthy, por ejemplo si la API se cuelga sin caerse. Este script, que corre cada minuto con un timer de systemd,
# reinicia los contenedores del proyecto marketplace que su healthcheck marcó unhealthy. Así no hace falta darle el
# socket de Docker a otro contenedor.
set -eu

PROYECTO=${PROYECTO:-marketplace}

for id in $(docker ps -q --filter "label=com.docker.compose.project=$PROYECTO" --filter health=unhealthy); do
    nombre=$(docker inspect --format '{{.Name}}' "$id")
    # Queda en el journal del sistema: journalctl -t marketplace-autorreparacion
    logger -t marketplace-autorreparacion "Reiniciando ${nombre#/}: su healthcheck lo marcó unhealthy"
    docker restart "$id" >/dev/null
done
