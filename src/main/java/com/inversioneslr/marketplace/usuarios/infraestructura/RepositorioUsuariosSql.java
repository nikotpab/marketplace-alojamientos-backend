package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.Usuario;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioUsuariosSql implements RepositorioUsuarios {

    private final JdbcClient jdbc;

    public RepositorioUsuariosSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        throw new UnsupportedOperationException("SQL de usuario");
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        throw new UnsupportedOperationException("SQL de usuario");
    }

    @Override
    public List<Usuario> listar() {
        throw new UnsupportedOperationException("SQL de usuario");
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        try {
            jdbc.sql("""
                    INSERT INTO usuario (id, nombre, apellido, correo, telefono, hash_contrasena, rol, cuenta_activa,
                                         restringido, motivo_restriccion, intentos_fallidos, bloqueado_hasta)
                    VALUES (:id, :nombre, :apellido, :correo, :telefono, :hash, :rol, :cuentaActiva,
                            :restringido, :motivo, :intentos, :bloqueadoHasta)
                    ON CONFLICT (id) DO UPDATE SET
                        nombre = EXCLUDED.nombre,
                        apellido = EXCLUDED.apellido,
                        correo = EXCLUDED.correo,
                        telefono = EXCLUDED.telefono,
                        hash_contrasena = EXCLUDED.hash_contrasena,
                        cuenta_activa = EXCLUDED.cuenta_activa,
                        restringido = EXCLUDED.restringido,
                        motivo_restriccion = EXCLUDED.motivo_restriccion,
                        intentos_fallidos = EXCLUDED.intentos_fallidos,
                        bloqueado_hasta = EXCLUDED.bloqueado_hasta""")
                    .param("id", usuario.id())
                    .param("nombre", usuario.nombre())
                    .param("apellido", usuario.apellido())
                    .param("correo", usuario.correo())
                    .param("telefono", usuario.telefono())
                    .param("hash", usuario.hashContrasena())
                    .param("rol", usuario.rol().name())
                    .param("cuentaActiva", usuario.cuentaActiva())
                    .param("restringido", usuario.restringido())
                    .param("motivo", usuario.motivoRestriccion())
                    .param("intentos", usuario.intentosFallidos())
                    .param("bloqueadoHasta", usuario.bloqueadoHasta() == null ? null : Timestamp.from(usuario.bloqueadoHasta()))
                    .update();
            return usuario;
        } catch (DuplicateKeyException e) {
            String mensaje = "Este correo ya está registrado. Inicia sesión o usa otro correo.";
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, mensaje, Map.of("correo", mensaje));
        }
    }
}
