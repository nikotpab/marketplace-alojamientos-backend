package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.cotizacion.api.Cotizacion;
import com.inversioneslr.marketplace.cotizacion.api.FachadaCotizacion;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas;
import com.inversioneslr.marketplace.reservas.aplicacion.ServicioReservas.ConfirmarReservaComando;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class IdempotenciaReservaIT extends PruebaIntegracion {

    private static final BigDecimal TOTAL = new BigDecimal("330000.00");

    @Autowired
    ServicioReservas reservas;

    @MockitoBean
    FachadaCotizacion cotizacion;

    @MockitoBean
    PasarelaPagos pasarela;

    private UUID huesped;
    private UUID publicacion;
    private ConfirmarReservaComando comando;

    @BeforeEach
    void preparar() {
        huesped = crearUsuario(Rol.HUESPED);
        publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        comando = new ConfirmarReservaComando(publicacion, LocalDate.of(2030, 9, 10), LocalDate.of(2030, 9, 13), 2, List.of());
        when(cotizacion.cotizar(any(), any(), any(), any())).thenReturn(new Cotizacion(3, new BigDecimal("300000.00"),
                BigDecimal.ZERO, new BigDecimal("30000.00"), BigDecimal.ZERO, BigDecimal.ZERO, TOTAL));
        // Como Stripe: la misma clave siempre produce el mismo pago.
        when(pasarela.cobrar(any(), any())).thenAnswer(llamada -> "pi_" + llamada.getArgument(1));
    }

    @Test
    void guardaLaClaveConLaReservaEnLaMismaTransaccion() {
        String clave = UUID.randomUUID().toString();

        Reserva reserva = reservas.confirmarReserva(huesped, comando, clave);

        var solicitud = comoAplicacion.queryForMap("SELECT huesped_id, reserva_id, resultado::text AS resultado FROM solicitud_procesada WHERE clave = ?", clave);
        assertThat(solicitud.get("huesped_id")).isEqualTo(huesped);
        assertThat(solicitud.get("reserva_id")).isEqualTo(reserva.id());
        assertThat(solicitud.get("resultado").toString()).contains("CONFIRMADA");
    }

    long contar(String sql, Object valor) {
        return comoAplicacion.queryForObject(sql, Long.class, valor);
    }
}
