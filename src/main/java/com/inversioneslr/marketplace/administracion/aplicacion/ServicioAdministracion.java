package com.inversioneslr.marketplace.administracion.aplicacion;

import com.inversioneslr.marketplace.auditoria.FachadaAuditoria;
import com.inversioneslr.marketplace.auditoria.FachadaAuditoria.EventoAuditoria;
import com.inversioneslr.marketplace.auditoria.FachadaAuditoria.FiltrosAuditoria;
import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.ResenaId;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import com.inversioneslr.marketplace.cotizacion.ConfiguracionTarifas;
import com.inversioneslr.marketplace.cotizacion.FachadaCotizacion;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones.TipoAlojamiento;
import com.inversioneslr.marketplace.resenas.FachadaResenas;
import com.inversioneslr.marketplace.reservas.FachadaReservas;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios.UsuarioResumen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moderación, auditoría y parámetros: delega en el módulo dueño de cada dato. */
@Service
public class ServicioAdministracion {

    private final FachadaPublicaciones publicaciones;
    private final FachadaReservas reservas;
    private final FachadaUsuarios usuarios;
    private final FachadaResenas resenas;
    private final FachadaCotizacion cotizacion;
    private final FachadaAuditoria auditoria;

    public ServicioAdministracion(FachadaPublicaciones publicaciones, FachadaReservas reservas, FachadaUsuarios usuarios,
                                  FachadaResenas resenas, FachadaCotizacion cotizacion, FachadaAuditoria auditoria) {
        this.publicaciones = publicaciones;
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.resenas = resenas;
        this.cotizacion = cotizacion;
        this.auditoria = auditoria;
    }

    /** CU-24: publicación BLOQUEADA y reservas futuras en revisión manual, todo o nada. */
    @Transactional
    public void bloquearPublicacion(UUID administradorId, UUID publicacionId, MotivoComando comando) {
        publicaciones.bloquear(new UsuarioId(administradorId), new PublicacionId(publicacionId), comando.motivo());
        reservas.marcarFuturasEnRevision(new PublicacionId(publicacionId));
    }

    public List<UsuarioResumen> listarUsuarios() {
        return usuarios.listar();
    }

    public void restringirUsuario(UUID administradorId, UUID usuarioId, MotivoComando comando) {
        usuarios.restringir(new UsuarioId(administradorId), new UsuarioId(usuarioId), comando.motivo());
    }

    public void ocultarResena(UUID administradorId, UUID resenaId) {
        resenas.ocultar(new UsuarioId(administradorId), new ResenaId(resenaId));
    }

    public List<EventoAuditoria> consultarAuditoria(FiltrosAuditoria filtros) {
        return auditoria.consultar(filtros);
    }

    public ConfiguracionTarifas consultarTarifas() {
        return cotizacion.consultarTarifas();
    }

    public void actualizarTarifas(UUID administradorId, ConfiguracionTarifas tarifas) {
        cotizacion.actualizarTarifas(new UsuarioId(administradorId), tarifas);
    }

    public List<TipoAlojamiento> listarTipos() {
        return publicaciones.listarTipos();
    }

    /** @param tipoId null para agregar un tipo nuevo */
    public TipoAlojamiento guardarTipo(UUID administradorId, UUID tipoId, TipoAlojamientoComando comando) {
        return publicaciones.guardarTipo(new UsuarioId(administradorId), new TipoAlojamiento(tipoId, comando.nombre(), comando.activo()));
    }

    public record MotivoComando(@NotBlank(message = "Indica el motivo.") String motivo) {
    }

    public record TipoAlojamientoComando(@NotBlank @Size(max = 120) String nombre, boolean activo) {
    }
}
