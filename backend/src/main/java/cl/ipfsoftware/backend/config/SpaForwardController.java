package cl.ipfsoftware.backend.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Reenvía las rutas del frontend a index.html cuando el backend también
 * sirve la interfaz compilada (SPA con BrowserRouter).
 *
 * <p>Las rutas de la API ({@code /api/**}) y los recursos estáticos
 * ({@code /assets/**}, {@code /index.html}) no pasan por aquí.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {"/", "/login", "/caja", "/productos", "/reportes", "/usuarios"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
