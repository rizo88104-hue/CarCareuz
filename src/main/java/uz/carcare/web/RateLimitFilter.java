package uz.carcare.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.carcare.util.RateLimiter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Sayt API sini spamdan himoya qiladi (IP bo'yicha):
 *  - umumiy: 1 daqiqada 60 ta so'rov
 *  - ro'yxatdan o'tish: 10 daqiqada 10 ta
 *  - sinov xabari: 10 daqiqada 3 ta (botni spam qilmaslik uchun)
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Umumiy limit IP bo'yicha. O'zbekistonda mobil operatorlar ko'p abonentni bitta IP orqali chiqaradi (CGNAT),
     * servisda ham bir nechta xodim bitta IP'da bo'ladi — shuning uchun limit yetarlicha keng.
     */
    private final RateLimiter general = new RateLimiter(300, 60_000);
    private final RateLimiter register = new RateLimiter(30, 600_000);
    private final RateLimiter test = new RateLimiter(3, 600_000);
    /** Admin parolini taxmin qilishdan himoya: 10 daqiqada 10 ta NOTO'G'RI urinish (to'g'ri kirishlar sanalmaydi). */
    private final RateLimiter login = new RateLimiter(10, 600_000);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = req.getRemoteAddr();
        String uri = req.getRequestURI();
        boolean post = "POST".equalsIgnoreCase(req.getMethod());

        boolean allowed = general.tryAcquire(ip);
        if (allowed && post && uri.equals("/api/cars")) allowed = register.tryAcquire(ip);
        if (allowed && post && uri.endsWith("/test")) allowed = test.tryAcquire(ip);
        boolean loginCall = post && uri.equals("/api/admin/login");
        if (allowed && loginCall && login.isLimited(ip)) allowed = false;

        if (!allowed) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write(loginCall
                    ? "{\"error\":\"Parol ko'p marta noto'g'ri kiritildi. 10 daqiqadan keyin urinib ko'ring.\"}"
                    : "{\"error\":\"Juda ko'p so'rov. Bir daqiqadan keyin urinib ko'ring.\"}");
            return;
        }
        chain.doFilter(req, res);
        // Faqat noto'g'ri login/parol hisobga olinadi
        if (loginCall && res.getStatus() == 401) login.tryAcquire(ip);
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        general.cleanup();
        register.cleanup();
        test.cleanup();
        login.cleanup();
    }
}
