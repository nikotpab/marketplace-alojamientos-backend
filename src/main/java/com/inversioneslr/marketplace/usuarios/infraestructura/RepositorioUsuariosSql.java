package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.Usuario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
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
        return jdbc.sql("SELECT * FROM usuario WHERE id = :id")
                .param("id", id)
                .query(RepositorioUsuariosSql::aUsuario)
                .optional();
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return jdbc.sql("SELECT * FROM usuario WHERE correo = :correo")
                .param("correo", correo)
                .query(RepositorioUsuariosSql::aUsuario)
                .optional();
    }

    @Override
    public List<Usuario> buscarPorIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT * FROM usuario WHERE id IN (:ids)")
                .param("ids", ids)
                .query(RepositorioUsuariosSql::aUsuario)
                .list();
    }

    @Override
    public List<Usuario> listar() {
        return jdbc.sql("SELECT * FROM usuario ORDER BY creado_en DESC")
                .query(RepositorioUsuariosSql::aUsuario)
                .list();
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        try {
            jdbc.sql("""
                    INSERT INTO usuario (id, nombre, apellido, correo, telefono, hash_contrasena, rol, cuenta_activa,
                                         restringido, motivo_restriccion, intentos_fallidos, bloqueado_hasta, correo_verificado)
                    VALUES (:id, :nombre, :apellido, :correo, :telefono, :hash, :rol, :cuentaActiva,
                            :restringido, :motivo, :intentos, :bloqueadoHasta, :correoVerificado)
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
                        bloqueado_hasta = EXCLUDED.bloqueado_hasta,
                        correo_verificado = EXCLUDED.correo_verificado""")
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
                    .param("correoVerificado", usuario.correoVerificado())
                    .update();
            return usuario;
        } catch (DuplicateKeyException e) {
            String mensaje = "Este correo ya está registrado. Inicia sesión o usa otro correo.";
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, mensaje, Map.of("correo", mensaje));
        }
    }

    private static Usuario aUsuario(ResultSet fila, int numero) throws SQLException {
        Timestamp bloqueadoHasta = fila.getTimestamp("bloqueado_hasta");
        return new Usuario(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                fila.getString("apellido"),
                fila.getString("correo"),
                fila.getString("telefono"),
                fila.getString("hash_contrasena"),
                Rol.valueOf(fila.getString("rol")),
                fila.getBoolean("cuenta_activa"),
                fila.getBoolean("restringido"),
                fila.getString("motivo_restriccion"),
                fila.getInt("intentos_fallidos"),
                bloqueadoHasta == null ? null : bloqueadoHasta.toInstant(),
                fila.getBoolean("correo_verificado"));
    }
}
