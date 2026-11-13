package cl.ipfsoftware.backend.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Aplica cabeceras de seguridad en cada respuesta para mitigar ataques comunes
 * (clickjacking, MIME-sniffing, ejecución de contenido no permitido, etc.).
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Evita que el navegador intente adivinar el tipo de contenido (MIME-sniffing).
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Evita que la página se inserte en iframes de otros sitios (clickjacking).
        response.setHeader("X-Frame-Options", "DENY");

        // HSTS solo cuando la conexión es segura (en producción detrás de HTTPS).
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        // Política de contenido básica: solo recursos del mismo origen.
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; frame-ancestors 'none'; base-uri 'self';"
        );

        // Referrer mínimo para no filtrar URLs completas a sitios externos.
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        filterChain.doFilter(request, response);
    }
}
