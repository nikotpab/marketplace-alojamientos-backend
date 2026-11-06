package com.inversioneslr.marketplace.usuarios.dominio;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.time.Instant;
import java.util.UUID;

public record Usuario(UUID id, String nombre, String apellido, String correo, String telefono, String hashContrasena,
                      Rol rol, boolean cuentaActiva, boolean restringido, String motivoRestriccion,
                      int intentosFallidos, Instant bloqueadoHasta) {

    public Usuario desactivar() {
        throw new UnsupportedOperationException("RF-05");
    }

    public Usuario restringir(String motivo) {
        throw new UnsupportedOperationException("RF-28");
    }
}
