package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.pagos.aplicacion.ServicioPagos;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** SCRUM-59 · RNF-12: ningún cobro queda sin reserva más de 15 min. */
class ReconciliacionIT extends PruebaIntegracion {

    private static final BigDecimal MONTO = new BigDecimal("330000.00");

    @MockitoBean
    PasarelaPagos pasarela;

    @Autowired
    ServicioPagos pagos;

    @Test
    void siLaAplicacionCaeTrasCobrarLaReconciliacionDevuelveElCobro() {
        String clave = UUID.randomUUID().toString();
        when(pasarela.cobrar(MONTO, clave)).thenReturn("pi_" + clave);
        pagos.cobrar(MONTO, clave);
        // La aplicación se cae aquí: no se guarda la reserva. Pasan 11 minutos.
        envejecer(clave, 11);

        pagos.revertirCobrosSinReserva();

        verify(pasarela).devolver("pi_" + clave, MONTO);
        assertThat(estado(clave)).isEqualTo("DEVUELTO");
        assertThat(comoAplicacion.queryForObject("SELECT count(*) FROM pago WHERE estado = 'PENDIENTE' AND clave_idempotencia = ?",
                Long.class, clave)).isZero();
        assertThat(comoAplicacion.queryForObject("""
                SELECT count(*) FROM auditoria.evento_auditoria a JOIN pago p ON p.id = a.entidad_id
                WHERE p.clave_idempotencia = ? AND a.accion = 'CobroRevertido'""", Long.class, clave)).isEqualTo(1);
    }

    @Test
    void unCobroEnCursoNoSeToca() {
        String clave = UUID.randomUUID().toString();
        when(pasarela.cobrar(MONTO, clave)).thenReturn("pi_" + clave);
        pagos.cobrar(MONTO, clave);
        envejecer(clave, 2);

        pagos.revertirCobrosSinReserva();

        verify(pasarela, never()).devolver(eq("pi_" + clave), any());
        assertThat(estado(clave)).isEqualTo("PENDIENTE");
    }

    @Test
    void unCobroQueLaPasarelaRechazaQuedaRechazadoSinDevolucion() {
        String clave = pendiente(11);
        when(pasarela.cobrar(MONTO, clave)).thenThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_RECHAZADO, "Rechazado"));

        pagos.revertirCobrosSinReserva();

        assertThat(estado(clave)).isEqualTo("RECHAZADO");
        verify(pasarela, never()).devolver(eq("pi_" + clave), any());
    }

    @Test
    void siLaPasarelaNoRespondeSeReintentaEnElSiguienteCiclo() {
        String clave = pendiente(11);
        when(pasarela.cobrar(MONTO, clave))
                .thenThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "Sin respuesta"))
                .thenReturn("pi_" + clave);

        pagos.revertirCobrosSinReserva();
        assertThat(estado(clave)).isEqualTo("PENDIENTE");

        pagos.revertirCobrosSinReserva();
        assertThat(estado(clave)).isEqualTo("DEVUELTO");
    }

    @Test
    void unPagoRechazadoAlConfirmarNoQuedaPendiente() {
        String clave = UUID.randomUUID().toString();
        when(pasarela.cobrar(MONTO, clave)).thenThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_RECHAZADO, "Rechazado"));

        try {
            pagos.cobrar(MONTO, clave);
        } catch (ExcepcionNegocio esperado) {
            // El huésped recibe el rechazo; el intento queda registrado.
        }

        assertThat(estado(clave)).isEqualTo("RECHAZADO");
    }

    private String pendiente(int minutosAtras) {
        String clave = UUID.randomUUID().toString();
        comoAplicacion.update("INSERT INTO pago (clave_idempotencia, monto, estado) VALUES (?, ?, 'PENDIENTE')", clave, MONTO);
        envejecer(clave, minutosAtras);
        return clave;
    }

    private void envejecer(String clave, int minutos) {
        comoAplicacion.update("UPDATE pago SET creado_en = now() - make_interval(mins => ?) WHERE clave_idempotencia = ?", minutos, clave);
    }

    private String estado(String clave) {
        return comoAplicacion.queryForObject("SELECT estado FROM pago WHERE clave_idempotencia = ?", String.class, clave);
    }
}
