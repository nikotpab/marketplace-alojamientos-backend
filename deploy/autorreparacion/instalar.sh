#!/bin/sh
# SCRUM-122: instala o actualiza el timer en la EC2, como root. Se puede repetir sin efectos de más.
set -eu
cd "$(dirname "$0")"
install -m 644 marketplace-autorreparacion.service marketplace-autorreparacion.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now marketplace-autorreparacion.timer
