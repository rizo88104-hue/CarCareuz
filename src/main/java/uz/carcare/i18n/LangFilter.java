package uz.carcare.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Har bir API so'rovining tilini aniqlaydi: X-Lang sarlavhasi → ?lang= → Accept-Language.
 * Sahifalar (index, car, admin) tanlangan tilni X-Lang bilan yuboradi.
 */
@Component
@Order(1)
public class LangFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String code = req.getHeader("X-Lang");
        if (code == null || code.isBlank()) code = req.getParameter("lang");
        if (code == null || code.isBlank()) code = req.getHeader("Accept-Language");
        LangContext.set(Lang.of(code));
        try {
            chain.doFilter(req, res);
        } finally {
            LangContext.clear();
        }
    }
}
