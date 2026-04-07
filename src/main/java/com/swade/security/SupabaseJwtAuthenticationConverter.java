package com.swade.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class SupabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Map<String, String> ROLE_MAPPING = Map.of(
            "Administrador", "ROLE_ADMINISTRADOR",
            "Clínico",       "ROLE_CLINICO",
            "Académico",     "ROLE_ACADEMICO"
    );

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String userRole = jwt.getClaimAsString("user_role");
        if (userRole == null) {
            return List.of();
        }
        String mapped = ROLE_MAPPING.get(userRole);
        if (mapped == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(mapped));
    }
}
