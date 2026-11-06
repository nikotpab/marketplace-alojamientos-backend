package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.compartido.dominio.Tokens;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokensJwt implements Tokens {

    private final String secreto;

    public TokensJwt(@Value("${marketplace.jwt-secreto}") String secreto) {
        this.secreto = secreto;
    }

    @Override
    public String emitir(UsuarioAutenticado usuario) {
        throw new UnsupportedOperationException("CU-02: emitir JWT");
    }

    /** Pendiente: validar el JWT. Mientras tanto ningún token es válido, así que las rutas protegidas se deniegan. */
    @Override
    public Optional<UsuarioAutenticado> verificar(String token) {
        return Optional.empty();
    }
}
