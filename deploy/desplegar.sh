#!/bin/bash
# Despliega en la EC2 una imagen que el CI ya publicó en ECR. Lo corre como root el documento de SSM que
# lanza cada push a main (ver "Despliegue en AWS" en el README).
#
#   desplegar.sh api <commit>   la API: .env desde SSM, usuario de la base y, si no queda sana, la imagen anterior
#   desplegar.sh web <commit>   los archivos del frontend que sirve Caddy
#
# Lo que imprime llega al log público de GitHub Actions: nunca muestra valores de los parámetros ni logs de la API.
set -euo pipefail

COMPONENTE=${1:?Indica api o web}
ETIQUETA=${2:?Indica la etiqueta de la imagen}
REGION=${REGION:-us-east-1}
RUTA_PARAMETROS=${RUTA_PARAMETROS:-/marketplace/prod}
# Sin estas la API no arranca, o no puede enviar el código del registro.
OBLIGATORIAS="DB_URL DB_USUARIO_APP DB_CONTRASENA_APP DB_USUARIO_MIGRACION DB_CONTRASENA_MIGRACION JWT_SECRETO
PAGOS_PROVEEDOR DOMINIO CORREO_SERVIDOR CORREO_PUERTO CORREO_USUARIO CORREO_CONTRASENA CORREO_SEGURO CORREO_REMITENTE"

cd /opt/marketplace
mkdir -p estado web/versiones
REGISTRO="$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$REGION.amazonaws.com"

# .env con los parámetros de la ruta, solo legible por root. Compose lo lee para las variables y para la API.
escribir_env() {
    umask 077
    aws ssm get-parameters-by-path --region "$REGION" --path "$RUTA_PARAMETROS" --with-decryption --output json \
        | OBLIGATORIAS="$OBLIGATORIAS" python3 -c '
import json, os, sys
valores = {p["Name"].rsplit("/", 1)[1]: p["Value"] for p in json.load(sys.stdin)["Parameters"]}
faltan = [nombre for nombre in os.environ["OBLIGATORIAS"].split() if not valores.get(nombre)]
if faltan:
    sys.exit("Faltan parámetros en SSM: " + ", ".join(faltan))
for nombre, valor in sorted(valores.items()):
    if nombre == "IMAGEN_API":  # la elige este script
        continue
    if "\x27" in valor or "\n" in valor:
        sys.exit("El parámetro " + nombre + " tiene comillas simples o saltos de línea, que .env no admite")
    print(nombre + "=\x27" + valor + "\x27")
' > .env.nuevo
    mv .env.nuevo .env
}

# Crea en RDS el usuario de la aplicación si no existe y le pone la contraseña de SSM: la migración V2 le da
# permisos y fallaría sin él. Así, cambiar la contraseña es actualizar el parámetro y volver a desplegar. Los secretos
# llegan a psql por variables de entorno, nunca por la línea de comandos.
preparar_base() (
    set -a
    . ./.env
    set +a
    servidor=${DB_URL#jdbc:postgresql://}
    export PGPASSWORD=$DB_CONTRASENA_MIGRACION USUARIO_APP=$DB_USUARIO_APP CLAVE_APP=$DB_CONTRASENA_APP
    docker run --rm -i --quiet -e PGPASSWORD -e USUARIO_APP -e CLAVE_APP postgres:17-alpine \
        psql -q -v ON_ERROR_STOP=1 "postgresql://$DB_USUARIO_MIGRACION@${servidor%%\?*}?sslmode=require" <<'SQL'
\getenv usuario USUARIO_APP
\getenv clave CLAVE_APP
SELECT format('CREATE ROLE %I LOGIN', :'usuario') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'usuario')
\gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'usuario', :'clave')
\gexec
SQL
)

# El healthcheck de la imagen tiene 60 s de gracia, y Flyway corre al arrancar: se espera hasta 5 minutos.
esperar_api() {
    local contenedor estado
    contenedor=$(docker compose ps -q marketplace-api)
    for _ in $(seq 60); do
        estado=$(docker inspect --format '{{.State.Health.Status}}' "$contenedor" 2>/dev/null || echo desconocido)
        [ "$estado" = healthy ] && return 0
        sleep 5
    done
    echo "Estado de la API: $estado" >&2
    return 1
}

# Deja en disco solo la imagen actual y la anterior de un repositorio.
limpiar_imagenes() {
    local repositorio=$1 actual=$2 anterior=${3:-ninguna}
    docker images "$repositorio" --format '{{.Repository}}:{{.Tag}}' \
        | { grep -vxF -e "$actual" -e "$anterior" || true; } \
        | xargs -r docker rmi > /dev/null 2>&1 || true
}

desplegar_api() {
    local imagen="$REGISTRO/marketplace-api:$ETIQUETA" anterior
    anterior=$(cat estado/api 2>/dev/null || true)
    preparar_base
    # Reinicia la API si se cuelga. El instalador se puede repetir.
    if [ -f autorreparacion/instalar.sh ]; then
        sh autorreparacion/instalar.sh
    fi
    docker pull -q "$imagen" > /dev/null
    IMAGEN_API="$imagen" docker compose up -d --remove-orphans
    # El Caddyfile pudo cambiar con los archivos de esta versión.
    docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile
    if esperar_api; then
        echo "$imagen" > estado/api
        limpiar_imagenes "$REGISTRO/marketplace-api" "$imagen" "$anterior"
        echo "API desplegada: $ETIQUETA"
        return
    fi
    echo "La API no quedó sana. Revisa /marketplace/api en CloudWatch Logs." >&2
    if [ -n "$anterior" ]; then
        IMAGEN_API="$anterior" docker compose up -d marketplace-api
        echo "Se volvió a la imagen anterior: ${anterior##*:}" >&2
    fi
    exit 1
}

desplegar_web() {
    local imagen="$REGISTRO/marketplace-web:$ETIQUETA" destino="web/versiones/$ETIQUETA" contenedor
    docker pull -q "$imagen" > /dev/null
    rm -rf "$destino"
    mkdir -p "$destino"
    contenedor=$(docker create "$imagen")
    docker cp "$contenedor:/srv/." "$destino/"
    docker rm "$contenedor" > /dev/null
    docker rmi "$imagen" > /dev/null 2>&1 || true
    if [ ! -f "$destino/index.html" ]; then
        echo "La imagen no trae index.html en /srv" >&2
        exit 1
    fi
    # Caddy sirve web/actual, un enlace que se reemplaza de una vez: nunca queda una versión a medias.
    ln -sfn "versiones/$ETIQUETA" web/actual.nuevo
    mv -Tf web/actual.nuevo web/actual
    echo "$ETIQUETA" > estado/web
    # Las 3 versiones más recientes quedan en disco para volver atrás sin descargar.
    ls -1t web/versiones | tail -n +4 | while read -r vieja; do rm -rf "web/versiones/$vieja"; done
    IMAGEN_API="$(cat estado/api 2>/dev/null || true)" docker compose up -d caddy
    echo "Frontend desplegado: $ETIQUETA"
}

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRO" > /dev/null
escribir_env
case "$COMPONENTE" in
    api) desplegar_api ;;
    web) desplegar_web ;;
    *) echo "Componente desconocido: $COMPONENTE (usa api o web)" >&2; exit 2 ;;
esac
