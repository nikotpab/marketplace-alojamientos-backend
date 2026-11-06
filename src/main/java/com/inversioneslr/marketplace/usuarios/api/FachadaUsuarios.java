package com.inversioneslr.marketplace.usuarios.api;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.util.List;
import java.util.UUID;

public interface FachadaUsuarios {

    List<UsuarioResumen> listar();

    /** CU-25: un usuario restringido no puede iniciar sesión. */
    void restringir(UUID administradorId, UUID usuarioId, String motivo);

    /** RNF-21: solo se muestran al anfitrión de una reserva confirmada con ese huésped. */
    DatosContacto datosContacto(UUID usuarioId);

    record UsuarioResumen(UUID id, String nombre, String apellido, String correo, String telefono, Rol rol,
                          boolean cuentaActiva, boolean restringido) {
    }

    record DatosContacto(String telefono, String correo) {
    }
}
