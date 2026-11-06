package com.inversioneslr.marketplace.administracion.presentacion;

import com.inversioneslr.marketplace.administracion.aplicacion.ServicioAdministracion;
import com.inversioneslr.marketplace.administracion.aplicacion.ServicioReportes;
import com.inversioneslr.marketplace.administracion.dominio.FormatoReporte;
import com.inversioneslr.marketplace.administracion.dominio.Indicadores;
import com.inversioneslr.marketplace.administracion.dominio.TipoReporte;
import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria.EventoAuditoria;
import com.inversioneslr.marketplace.auditoria.api.FachadaAuditoria.FiltrosAuditoria;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.cotizacion.api.ConfiguracionTarifas;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.TipoAlojamiento;
import com.inversioneslr.marketplace.usuarios.api.FachadaUsuarios.UsuarioResumen;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class ControladorAdministracion {

    private final ServicioAdministracion administracion;
    private final ServicioReportes reportes;

    public ControladorAdministracion(ServicioAdministracion administracion, ServicioReportes reportes) {
        this.administracion = administracion;
        this.reportes = reportes;
    }

    @PostMapping("/publicaciones/{id}/bloqueo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bloquearPublicacion(@AuthenticationPrincipal UsuarioAutenticado administrador, @PathVariable UUID id,
                                    @Valid @RequestBody ServicioAdministracion.MotivoComando comando) {
        administracion.bloquearPublicacion(administrador.id(), id, comando);
    }

    @GetMapping("/usuarios")
    public List<UsuarioResumen> listarUsuarios() {
        return administracion.listarUsuarios();
    }

    @PostMapping("/usuarios/{id}/restriccion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restringirUsuario(@AuthenticationPrincipal UsuarioAutenticado administrador, @PathVariable UUID id,
                                  @Valid @RequestBody ServicioAdministracion.MotivoComando comando) {
        administracion.restringirUsuario(administrador.id(), id, comando);
    }

    @PostMapping("/resenas/{id}/ocultamiento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ocultarResena(@AuthenticationPrincipal UsuarioAutenticado administrador, @PathVariable UUID id) {
        administracion.ocultarResena(administrador.id(), id);
    }

    @GetMapping("/auditoria")
    public List<EventoAuditoria> consultarAuditoria(FiltrosAuditoria filtros) {
        return administracion.consultarAuditoria(filtros);
    }

    @GetMapping("/tarifas")
    public ConfiguracionTarifas consultarTarifas() {
        return administracion.consultarTarifas();
    }

    @PutMapping("/tarifas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarTarifas(@AuthenticationPrincipal UsuarioAutenticado administrador,
                                  @Valid @RequestBody ConfiguracionTarifas tarifas) {
        administracion.actualizarTarifas(administrador.id(), tarifas);
    }

    @GetMapping("/tipos-alojamiento")
    public List<TipoAlojamiento> listarTipos() {
        return administracion.listarTipos();
    }

    @PostMapping("/tipos-alojamiento")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoAlojamiento agregarTipo(@AuthenticationPrincipal UsuarioAutenticado administrador,
                                       @Valid @RequestBody ServicioAdministracion.TipoAlojamientoComando comando) {
        return administracion.guardarTipo(administrador.id(), null, comando);
    }

    @PutMapping("/tipos-alojamiento/{id}")
    public TipoAlojamiento editarTipo(@AuthenticationPrincipal UsuarioAutenticado administrador, @PathVariable UUID id,
                                      @Valid @RequestBody ServicioAdministracion.TipoAlojamientoComando comando) {
        return administracion.guardarTipo(administrador.id(), id, comando);
    }

    @GetMapping("/dashboard")
    public Indicadores consultarDashboard() {
        return reportes.consultarDashboard();
    }

    @GetMapping("/reportes")
    public CompletableFuture<ResponseEntity<byte[]>> exportarReporte(@AuthenticationPrincipal UsuarioAutenticado administrador,
                                                                     @RequestParam TipoReporte tipo,
                                                                     @RequestParam FormatoReporte formato) {
        return reportes.exportar(administrador.id(), tipo, formato).thenApply(contenido -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte." + formato.extension() + "\"")
                .body(contenido));
    }
}
