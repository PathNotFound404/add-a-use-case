package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import team.projectpulse.user.UserRepository;

/**
 * Turns a valid JWT into an authentication only while the account it was issued to is still active
 * (FR-SEC-active-account).
 *
 * <p>A JWT is self-contained and lives for two hours, so without this check a student or instructor deactivated
 * after logging in would keep working until her token expired. This converter delegates to the
 * {@link JwtAuthenticationConverter} bean (which maps the {@code authorities} claim), then asks whether the
 * {@code userId} claim names an existing, enabled account: one primary-key existence query per request. If not,
 * it throws {@link DisabledException}; {@code BearerTokenAuthenticationFilter} hands that to the bearer entry
 * point, and {@code ExceptionHandlerAdvice} answers {@code 401} "User account is abnormal.", which the SPA treats
 * as a logout.
 *
 * <p>It is wired explicitly in {@code SecurityConfiguration} with {@code jwt.jwtAuthenticationConverter(..)}.
 * It is deliberately not exposed as the {@code JwtAuthenticationConverter} bean: Spring Security looks that bean up
 * by its exact type, so a bean of any other type would be ignored, silently, in favour of a default converter.
 */
@Component
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;
    private final UserRepository userRepository;


    public ActiveAccountJwtAuthenticationConverter(JwtAuthenticationConverter jwtAuthenticationConverter, UserRepository userRepository) {
        this.delegate = jwtAuthenticationConverter;
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AbstractAuthenticationToken authentication = this.delegate.convert(jwt);
        Integer userId = readUserId(jwt);
        if (userId == null || !this.userRepository.existsByIdAndEnabledTrue(userId)) {
            throw new DisabledException("The account this token was issued to is deactivated or no longer exists.");
        }
        return authentication;
    }

    /**
     * Reads the {@code userId} claim, which {@code JwtProvider} writes as a number. A missing or malformed claim
     * yields {@code null}, which the caller refuses: a token that names no account authenticates no one.
     */
    private static Integer readUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) {
            return number.intValue();
        }
        if (claim instanceof String s) {
            try {
                return Integer.valueOf(s);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

}
