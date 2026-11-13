package cl.ipfsoftware.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro de rate limiting para el endpoint de login.
 * Permite un máximo de 5 intentos fallidos por IP en una ventana de 10 minutos.
 * Superado el límite, responde HTTP 429 (Too Many Requests).
 */
@Component
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 600; // 10 minutos

    private static final class Attempt {
        int count;
        Instant windowStart;

        Attempt(Instant windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }
    }

    private final Map<String, Attempt> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Solo aplicamos rate limiting a peticiones POST al login.
        if (!"/api/auth/login".equals(path) || !"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        Instant now = Instant.now();

        Attempt attempt = attemptsByIp.compute(ip, (key, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart.plusSeconds(WINDOW_SECONDS))) {
                return new Attempt(now);
            }
            existing.count++;
            return existing;
        });

        if (attempt.count > MAX_ATTEMPTS) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"message\":\"Demasiados intentos de inicio de sesión. Intente nuevamente en 10 minutos.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xreal = request.getHeader("X-Real-IP");
        if (xreal != null && !xreal.isBlank()) {
            return xreal;
        }
        return request.getRemoteAddr();
    }

    /**
     * Registra un intento fallido para la IP dada, para contabilizarlo en el rate limiter.
     * Es llamado desde el punto de entrada de autenticación cuando las credenciales son inválidas.
     */
    public void recordFailedAttempt(String ip) {
        Instant now = Instant.now();
        attemptsByIp.compute(ip, (key, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart.plusSeconds(WINDOW_SECONDS))) {
                return new Attempt(now);
            }
            existing.count++;
            return existing;
        });
    }

    Map<String, Attempt> getAttemptsByIp() {
        return attemptsByIp;
    }
}
