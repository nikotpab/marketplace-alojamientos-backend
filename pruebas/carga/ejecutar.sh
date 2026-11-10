#!/bin/bash
# SCRUM-67 · ASR-01: prepara un huésped y un administrador, corre plan.jmx y resume p95 y p99 por escenario.
# Requiere el backend con el perfil demo en http://localhost:8080, su base en Docker y JMeter 5.6.
# Uso: pruebas/carga/ejecutar.sh [segundos, 120 por defecto]
# Si la base corre con otro nombre de proyecto de Docker Compose, indícalo en PROYECTO_COMPOSE.
set -euo pipefail
cd "$(dirname "$0")"
RAIZ=$(cd ../.. && pwd)
BASE=${BASE:-http://localhost:8080}
DURACION=${1:-120}
CONTRASENA="Carga-de-prueba-1"
SALIDA="resultados/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$SALIDA"

registrar() {
    curl -sf -X POST "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' -o /dev/null \
        -d "{\"nombre\":\"Carga\",\"apellido\":\"Prueba\",\"correo\":\"$1\",\"telefono\":\"3000000000\",\"contrasena\":\"$CONTRASENA\",\"rol\":\"HUESPED\"}"
}

token() {
    curl -sf -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
        -d "{\"correo\":\"$1\",\"contrasena\":\"$CONTRASENA\"}" | python3 -c 'import json, sys; print(json.load(sys.stdin)["token"])'
}

SUFIJO=$(date +%s)
HUESPED="huesped-$SUFIJO@carga.co"
ADMINISTRADOR="administrador-$SUFIJO@carga.co"
registrar "$HUESPED"
registrar "$ADMINISTRADOR"
sql() {
    docker compose -f "$RAIZ/compose.yaml" ${PROYECTO_COMPOSE:+-p "$PROYECTO_COMPOSE"} exec -T db \
        psql -q -U marketplace_owner -d marketplace -c "$1" 2>&1 | grep -v 'is not set' || true
}
# El rol de administrador no se elige al registrarse (CU-01).
sql "UPDATE marketplace.usuario SET rol = 'ADMINISTRADOR' WHERE correo = '$ADMINISTRADOR'"

read -r PUBLICACION CIUDAD < <(curl -sf "$BASE/api/v1/alojamientos?tamano=1" | python3 -c '
import json, sys, urllib.parse
alojamiento = json.load(sys.stdin)["contenido"][0]
print(alojamiento["id"], urllib.parse.quote(alojamiento["ciudad"]))')

echo "Carga de $DURACION s sobre la publicación $PUBLICACION. Resultados en pruebas/carga/$SALIDA"
jmeter -n -t plan.jmx -l "$SALIDA/resultados.jtl" -e -o "$SALIDA/reporte" \
    -Jduracion="$DURACION" -Jpublicacion="$PUBLICACION" -Jciudad="$CIUDAD" \
    -Jtoken_huesped="$(token "$HUESPED")" -Jtoken_administrador="$(token "$ADMINISTRADOR")" > "$SALIDA/jmeter.log"
python3 resumen.py "$SALIDA/resultados.jtl" | tee "$SALIDA/resumen.md"

# Borra las reservas de la prueba para no dejar ocupado el calendario de la demo. Los usuarios quedan, porque la
# auditoría, que es de solo inserción, los referencia.
DE_LA_PRUEBA="SELECT r.id FROM marketplace.reserva r JOIN marketplace.usuario u ON u.id = r.huesped_id WHERE u.correo = '$HUESPED'"
sql "DELETE FROM marketplace.cancelacion WHERE reserva_id IN ($DE_LA_PRUEBA);
     DELETE FROM marketplace.pago WHERE reserva_id IN ($DE_LA_PRUEBA);
     DELETE FROM marketplace.solicitud_procesada WHERE huesped_id = (SELECT id FROM marketplace.usuario WHERE correo = '$HUESPED');
     DELETE FROM marketplace.reserva WHERE id IN ($DE_LA_PRUEBA);"
