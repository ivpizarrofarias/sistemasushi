package cl.ipfsoftware.backend.web;

import cl.ipfsoftware.backend.dto.CashSessionDto;
import cl.ipfsoftware.backend.dto.CloseCashRequest;
import cl.ipfsoftware.backend.dto.OpenCashRequest;
import cl.ipfsoftware.backend.service.CashService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash")
public class CashController {

    private final CashService cashService;

    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    @GetMapping("/current")
    public CashSessionDto current() {
        return cashService.current();
    }

    @PostMapping("/open")
    public CashSessionDto open(@Valid @RequestBody OpenCashRequest request, Authentication authentication) {
        return cashService.open(request, authentication.getName());
    }

    @PostMapping("/close")
    public CashSessionDto close(@Valid @RequestBody CloseCashRequest request, Authentication authentication) {
        return cashService.close(request, authentication.getName());
    }

    @GetMapping("/sessions")
    public List<CashSessionDto> history() {
        return cashService.history();
    }
}
