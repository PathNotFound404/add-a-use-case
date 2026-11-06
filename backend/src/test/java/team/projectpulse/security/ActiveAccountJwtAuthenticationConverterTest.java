package team.projectpulse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import team.projectpulse.user.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * FR-SEC-active-account at the unit level: a valid token authenticates only while the account it names is active.
 */
@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtAuthenticationConverterTest {

    @Mock
    UserRepository userRepository;


    private ActiveAccountJwtAuthenticationConverter converter() {
        // The same delegate SecurityConfiguration builds: authorities from the "authorities" claim, no prefix
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("authorities");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(authorities);
        return new ActiveAccountJwtAuthenticationConverter(delegate, this.userRepository);
    }

    private static Jwt token(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("j.smith@abc.edu")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("authorities", "ROLE_student");
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }

    @Test
    @DisplayName("An active account authenticates, with the authorities its token carries")
    void activeAccountAuthenticates() {
        given(this.userRepository.existsByIdAndEnabledTrue(4)).willReturn(true);

        AbstractAuthenticationToken authentication = converter().convert(token(4L));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_student");
    }

    @Test
    @DisplayName("A deactivated, or deleted, account is refused although its token is still valid")
    void deactivatedAccountIsRefused() {
        given(this.userRepository.existsByIdAndEnabledTrue(4)).willReturn(false);

        Throwable throwable = catchThrowable(() -> converter().convert(token(4L)));

        assertThat(throwable).isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("A token that names no account is refused without a lookup")
    void tokenWithoutUserIdIsRefused() {
        Throwable missing = catchThrowable(() -> converter().convert(token(null)));
        Throwable malformed = catchThrowable(() -> converter().convert(token("not-a-number")));

        assertThat(missing).isInstanceOf(DisabledException.class);
        assertThat(malformed).isInstanceOf(DisabledException.class);
        verifyNoInteractions(this.userRepository);
    }

}
