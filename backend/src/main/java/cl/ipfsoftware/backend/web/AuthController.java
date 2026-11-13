package cl.ipfsoftware.backend.web;

import cl.ipfsoftware.backend.config.LoginRateLimitingFilter;
import cl.ipfsoftware.backend.domain.User;
import cl.ipfsoftware.backend.dto.LoginRequest;
import cl.ipfsoftware.backend.dto.UserDto;
import cl.ipfsoftware.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository;
    private final LoginRateLimitingFilter loginRateLimitingFilter;

    public AuthController(
            UserService userService,
            SecurityContextRepository securityContextRepository,
            LoginRateLimitingFilter loginRateLimitingFilter
    ) {
        this.userService = userService;
        this.securityContextRepository = securityContextRepository;
        this.loginRateLimitingFilter = loginRateLimitingFilter;
    }

    @GetMapping("/csrf-token")
    public CsrfToken csrfToken(HttpServletRequest request) {
        return (CsrfToken) request.getAttribute("_csrf");
    }

    @PostMapping("/login")
    public UserDto login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String ip = httpRequest.getRemoteAddr();
        try {
            User user = userService.authenticate(request.username(), request.password());

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user.getUsername(), null, authorities);

            // Protección contra fijación de sesión: se descarta cualquier sesión previa
            // antes de guardar la autenticación en una sesión nueva.
            HttpSession previousSession = httpRequest.getSession(false);
            if (previousSession != null) {
                previousSession.invalidate();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            httpRequest.getSession(true);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            return UserDto.from(user);
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            loginRateLimitingFilter.recordFailedAttempt(ip);
            throw ex;
        }
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return UserDto.from(userService.requireByUsername(authentication.getName()));
    }
}
