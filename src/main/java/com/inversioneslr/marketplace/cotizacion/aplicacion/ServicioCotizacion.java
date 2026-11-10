package com.inversioneslr.marketplace.cotizacion.aplicacion;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.cotizacion.api.ConfiguracionTarifas;
import com.inversioneslr.marketplace.cotizacion.api.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.api.FachadaCotizacion;
import com.inversioneslr.marketplace.cotizacion.dominio.RepositorioTarifas;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.DatosPublicacion;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones.ServicioAdicional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicioCotizacion implements FachadaCotizacion {

    private static final String MONEDA = "COP";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final RepositorioTarifas tarifas;
    private final FachadaPublicaciones publicaciones;
    private final PublicadorEventos eventos;
    private final Clock reloj;

    public ServicioCotizacion(RepositorioTarifas tarifas, FachadaPublicaciones publicaciones, PublicadorEventos eventos, Clock reloj) {
        this.tarifas = tarifas;
        this.publicaciones = publicaciones;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * CU-16 desde la pantalla de cotización: valida las fechas, el estado, la capacidad y la disponibilidad antes de
     * calcular. No crea reserva ni bloquea el periodo.
     */
    public Desglose cotizarParaHuesped(UUID publicacionId, LocalDate llegada, LocalDate salida, int huespedes, List<UUID> adicionales) {
        if (!salida.isAfter(llegada)) {
            throw datoInvalido("salida", "La salida debe ser posterior a la llegada. Elige una fecha de salida más tardía.");
        }
        if (llegada.isBefore(LocalDate.now(reloj))) {
            throw datoInvalido("llegada", "La llegada ya pasó. Elige una fecha de hoy en adelante.");
        }
        DatosPublicacion publicacion = publicaciones.datosPublicacion(publicacionId);
        if (!publicacion.activa()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "El alojamiento no está disponible.");
        }
        if (huespedes > publicacion.capacidad()) {
            throw datoInvalido("huespedes", "El alojamiento admite hasta " + publicacion.capacidad() + " huéspedes. Reduce el número.");
        }
        if (!publicaciones.periodoDisponible(publicacionId, llegada, salida)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "Las fechas elegidas no están disponibles. Elige otras en el calendario.");
        }
        return desglosar(publicacion, llegada, salida, adicionales);
    }

    /** CU-17 la incluye después de validar la reserva. */
    @Override
    public Cotizacion cotizar(UUID publicacionId, LocalDate llegada, LocalDate salida, List<UUID> serviciosAdicionales) {
        Desglose desglose = desglosar(publicaciones.datosPublicacion(publicacionId), llegada, salida, serviciosAdicionales);
        return new Cotizacion(desglose.noches(), desglose.subtotal(), desglose.tarifaLimpieza(), desglose.tarifaServicio(),
                suma(desglose.descuentos()), suma(desglose.adicionales()), desglose.total());
    }

    /**
     * La tarifa de servicio y los descuentos son porcentajes del subtotal, como en el mockup de la cotización, y se
     * redondean al peso. Los porcentajes se leen en cada cotización, así que un cambio aplica de inmediato (RNF-29).
     */
    private Desglose desglosar(DatosPublicacion publicacion, LocalDate llegada, LocalDate salida, List<UUID> seleccionados) {
        ConfiguracionTarifas vigentes = tarifas.vigentes();
        int noches = (int) ChronoUnit.DAYS.between(llegada, salida);
        BigDecimal subtotal = publicacion.precioNoche().multiply(BigDecimal.valueOf(noches));
        BigDecimal limpieza = porcentaje(subtotal, vigentes.porcentajeLimpieza());
        BigDecimal servicio = porcentaje(subtotal, vigentes.porcentajeServicio());

        List<Linea> descuentos = new ArrayList<>();
        agregarDescuento(descuentos, "Descuento promocional", subtotal, vigentes.porcentajeDescuento());
        if (vigentes.nochesEstanciaLarga() != null && noches >= vigentes.nochesEstanciaLarga()) {
            agregarDescuento(descuentos, "Descuento por estancia larga", subtotal, vigentes.porcentajeDescuentoEstanciaLarga());
        }
        List<Linea> adicionales = adicionalesElegidos(publicacion, seleccionados);
        BigDecimal total = subtotal.add(limpieza).add(servicio).add(suma(adicionales)).subtract(suma(descuentos));

        List<AdicionalDisponible> disponibles = publicacion.serviciosAdicionales().stream()
                .map(adicional -> new AdicionalDisponible(adicional.id(), adicional.nombre(), adicional.costo()))
                .toList();
        return new Desglose(noches, publicacion.precioNoche(), subtotal, limpieza, vigentes.porcentajeServicio(), servicio,
                descuentos, adicionales, total, MONEDA, disponibles);
    }

    private static List<Linea> adicionalesElegidos(DatosPublicacion publicacion, List<UUID> seleccionados) {
        if (seleccionados == null || seleccionados.isEmpty()) {
            return List.of();
        }
        List<ServicioAdicional> elegidos = publicacion.serviciosAdicionales().stream()
                .filter(adicional -> seleccionados.contains(adicional.id()))
                .toList();
        if (elegidos.size() != seleccionados.stream().distinct().count()) {
            throw datoInvalido("adicionales", "Algún servicio adicional no pertenece a este alojamiento. Elige solo los que ofrece.");
        }
        return elegidos.stream().map(adicional -> new Linea(adicional.nombre(), adicional.costo())).toList();
    }

    private static void agregarDescuento(List<Linea> descuentos, String concepto, BigDecimal subtotal, BigDecimal porcentaje) {
        if (porcentaje.signum() > 0) {
            descuentos.add(new Linea(concepto + " (" + porcentaje.stripTrailingZeros().toPlainString() + " %)", porcentaje(subtotal, porcentaje)));
        }
    }

    private static BigDecimal porcentaje(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(CIEN, 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal suma(List<Linea> lineas) {
        return lineas.stream().map(Linea::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ExcepcionNegocio datoInvalido(String campo, String mensaje) {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of(campo, mensaje));
    }

    @Override
    public ConfiguracionTarifas consultarTarifas() {
        return tarifas.vigentes();
    }

    @Override
    public void actualizarTarifas(UUID administradorId, ConfiguracionTarifas nuevasTarifas) {
        throw new UnsupportedOperationException("CU-28");
    }

    /** El desglose que muestra la pantalla de cotización del frontend (RNF-34). */
    public record Desglose(int noches, BigDecimal precioNoche, BigDecimal subtotal, BigDecimal tarifaLimpieza,
                           BigDecimal porcentajeServicio, BigDecimal tarifaServicio, List<Linea> descuentos,
                           List<Linea> adicionales, BigDecimal total, String moneda, List<AdicionalDisponible> adicionalesDisponibles) {
    }

    public record Linea(String concepto, BigDecimal monto) {
    }

    public record AdicionalDisponible(UUID id, String nombre, BigDecimal precio) {
    }
}
