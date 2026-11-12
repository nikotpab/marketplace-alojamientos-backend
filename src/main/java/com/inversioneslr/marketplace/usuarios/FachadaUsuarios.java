package com.inversioneslr.marketplace.usuarios;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FachadaUsuarios {

    List<UsuarioResumen> listar();

    /** CU-25: un usuario restringido no puede iniciar sesión. */
    void restringir(UUID administradorId, UUID usuarioId, String motivo);

    /** RNF-21: solo se muestran al anfitrión de una reserva confirmada con ese huésped. */
    DatosContacto datosContacto(UUID usuarioId);

    /**
     * SCRUM-142: los otros módulos ya no leen la tabla usuario. Una sola consulta para todos los ids; los que no
     * existen no aparecen. Cada módulo arma el nombre como lo muestra.
     */
    Map<UUID, Persona> personas(Collection<UUID> usuarioIds);

    record UsuarioResumen(UUID id, String nombre, String apellido, String correo, String telefono, Rol rol,
                          boolean cuentaActiva, boolean restringido, boolean correoVerificado) {
    }

    record DatosContacto(String telefono, String correo) {
    }

    /** Nombre y apellido tal como están guardados: pueden ser null. */
    record Persona(String nombre, String apellido, Rol rol) {
    }
}
