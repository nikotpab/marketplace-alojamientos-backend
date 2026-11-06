package com.inversioneslr.marketplace.compartido.dominio;

import java.util.Optional;

/** Tokens de sesión. Lo implementa el módulo usuarios. */
public interface Tokens {

    String emitir(UsuarioAutenticado usuario);

    Optional<UsuarioAutenticado> verificar(String token);
}
