package com.inversioneslr.marketplace.pagos.aplicacion;

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

    @Override
    public void registrarPago(UUID reservaId, String referencia, String claveSolicitud, BigDecimal monto) {
        pagos.guardar(new Pago(UUID.randomUUID(), reservaId, referencia, claveSolicitud, monto, Pago.Estado.APROBADO));
    }
}
