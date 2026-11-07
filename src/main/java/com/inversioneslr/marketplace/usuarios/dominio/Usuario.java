package com.inversioneslr.marketplace.usuarios.dominio;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Usuario(UUID id, String nombre, String apellido, String correo, String telefono, String hashContrasena,
                      Rol rol, boolean cuentaActiva, boolean restringido, String motivoRestriccion,
                      int intentosFallidos, Instant bloqueadoHasta) {

    public boolean bloqueadoEn(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /** Al llegar al máximo de intentos seguidos, bloquea la cuenta y vuelve a contar desde cero. */
    public Usuario registrarIntentoFallido(Instant ahora, int maximoIntentos, Duration bloqueo) {
        int intentos = intentosFallidos + 1;
        return intentos >= maximoIntentos ? conIntentos(0, ahora.plus(bloqueo)) : conIntentos(intentos, bloqueadoHasta);
    }

    public Usuario reiniciarIntentos() {
        return conIntentos(0, null);
    }

    private Usuario conIntentos(int intentos, Instant hasta) {
        return new Usuario(id, nombre, apellido, correo, telefono, hashContrasena, rol, cuentaActiva, restringido,
                motivoRestriccion, intentos, hasta);
    }

    public Usuario desactivar() {
        throw new UnsupportedOperationException("RF-05");
    }

    public Usuario restringir(String motivo) {
        throw new UnsupportedOperationException("RF-28");
    }
}
