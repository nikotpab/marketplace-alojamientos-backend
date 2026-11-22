#!/bin/bash
# Prepara una vez la EC2 (Amazon Linux 2023) para el despliegue continuo. Se corre como root por SSM, como
# indica "Despliegue en AWS" en el README. Se puede repetir.
set -euo pipefail
VERSION_COMPOSE=v5.8.0

dnf install -y -q docker
systemctl enable --now docker

# Docker Compose no está en los repositorios de Amazon Linux 2023: se instala el binario oficial y se verifica su hash.
binario="https://github.com/docker/compose/releases/download/$VERSION_COMPOSE/docker-compose-linux-$(uname -m)"
destino=/usr/local/lib/docker/cli-plugins/docker-compose
mkdir -p "$(dirname "$destino")"
curl -fsSL -o "$destino" "$binario"
echo "$(curl -fsSL "$binario.sha256" | cut -d' ' -f1)  $destino" | sha256sum -c --quiet -
chmod 755 "$destino"

# Lo que deploy/desplegar.sh espera encontrar. Solo root entra: ahí queda el .env con los secretos.
install -d -m 700 /opt/marketplace
install -d -m 755 /opt/marketplace/estado /opt/marketplace/web /opt/marketplace/web/versiones

docker compose version
