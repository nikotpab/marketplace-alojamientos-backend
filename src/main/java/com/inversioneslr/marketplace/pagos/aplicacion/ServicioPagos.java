package com.inversioneslr.marketplace.pagos.aplicacion;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.pagos.FachadaPagos;
import com.inversioneslr.marketplace.pagos.dominio.Pago;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import com.inversioneslr.marketplace.pagos.dominio.RepositorioPagos;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** CU-19. */
@Service
public class ServicioPagos implements FachadaPagos {

    private final PasarelaPagos pasarela;
    private final RepositorioPagos pagos;

    public ServicioPagos(PasarelaPagos pasarela, RepositorioPagos pagos) {
        this.pasarela = pasarela;
        this.pagos = pagos;
    }

    @Override
    public String cobrar(BigDecimal monto, String claveSolicitud) {
        return pasarela.cobrar(monto, claveSolicitud);
    }

    @Override
    public void devolver(String referencia, BigDecimal monto) {
        pasarela.devolver(referencia, monto);
    }

    /** Marca el pago antes de llamar a la pasarela: así dos cancelaciones simultáneas no devuelven dos veces. */
    @Override
    public void devolverPagoDeReserva(UUID reservaId, BigDecimal monto) {
        String referencia = pagos.marcarDevuelto(reservaId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, "La reserva no tiene un pago aprobado que devolver."));
        try {
            pasarela.devolver(referencia, monto);
        } catch (RuntimeException e) {
            pagos.marcarAprobado(reservaId);
            throw e;
        }
    }

    @Override
    public void registrarPago(UUID reservaId, String referencia, String claveSolicitud, BigDecimal monto) {
        pagos.guardar(new Pago(UUID.randomUUID(), reservaId, referencia, claveSolicitud, monto, Pago.Estado.APROBADO));
    }
}
