"""Resume el .jtl de JMeter: ritmo, errores, p95 y p99 por escenario, comparados con las metas de RNF-01 a RNF-08."""
import csv
import math
import sys

# Escenario: (ritmo pedido por minuto, meta p95 en ms, meta p99 en ms)
METAS = {
    "busqueda": ("580", 1000, 2000),
    "detalle": ("580", 2000, None),
    "calendario": ("580", 2500, None),
    "cotizacion": ("100", 500, 1000),
    "confirmacion": ("30", 5000, None),
    "cancelacion": ("10", 4000, None),
    "dashboard": ("5 administradores", 4000, None),
    "reporte": ("5 exportaciones", 10000, None),
}


def percentil(valores, porcentaje):
    ordenados = sorted(valores)
    return ordenados[max(math.ceil(porcentaje / 100 * len(ordenados)) - 1, 0)]


tiempos, errores, instantes = {}, {}, []
with open(sys.argv[1], newline="", encoding="utf-8") as archivo:
    for fila in csv.DictReader(archivo):
        tiempos.setdefault(fila["label"], []).append(int(fila["elapsed"]))
        instantes.append(int(fila["timeStamp"]))
        if fila["success"] != "true":
            errores[fila["label"]] = errores.get(fila["label"], 0) + 1

minutos = (max(instantes) - min(instantes)) / 60000
print("| Escenario | Pedido | Logrado por minuto | Errores | p95 (meta) | p99 (meta) | Cumple |")
print("|---|---|---|---|---|---|---|")
for escenario, (pedido, meta95, meta99) in METAS.items():
    valores = tiempos.get(escenario, [])
    if not valores:
        print(f"| {escenario} | {pedido} | sin muestras | | | | no |")
        continue
    p95, p99 = percentil(valores, 95), percentil(valores, 99)
    cumple = p95 <= meta95 and (meta99 is None or p99 <= meta99) and not errores.get(escenario)
    print(f"| {escenario} | {pedido} | {len(valores) / minutos:.0f} | {errores.get(escenario, 0)} | {p95} ms (≤ {meta95}) | "
          f"{p99} ms{f' (≤ {meta99})' if meta99 else ''} | {'sí' if cumple else 'no'} |")
