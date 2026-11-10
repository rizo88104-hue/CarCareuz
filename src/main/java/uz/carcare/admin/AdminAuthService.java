package uz.carcare.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uz.carcare.util.IpAllowList;
import uz.carcare.i18n.I18n;
import uz.carcare.i18n.LangContext;
import uz.carcare.model.AdminSession;
import uz.carcare.model.StaffUser;
import uz.carcare.model.StaffUser.Role;
import uz.carcare.repo.AdminSessionRepository;
import uz.carcare.repo.StaffUserRepository;
import uz.carcare.util.Formats;
import uz.carcare.web.ApiException;

import java.time.Duration;
import java.time.Instant;

/**
 * Admin panelga kirish va huquqlarni tekshirish.
 * Birinchi ishga tushishda super admin yaratiladi: login va parol
 * SUPERADMIN_USERNAME / SUPERADMIN_PASSWORD dan olinadi. Parol berilmasa,
 * tasodifiy parol yaratilib, konsolga bir marta chiqariladi.
 */
@Service
public class AdminAuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final StaffUserRepository staff;
    private final AdminSessionRepository sessions;
    private final AuditService audit;
    private final AdminTwoFactorService twoFactor;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String superUsername;
    private final String superPassword;
    /** Super admin faqat shu IP lardan kira oladi (bo'sh — cheklov yo'q). */
    private final IpAllowList superIps;

    public AdminAuthService(StaffUserRepository staff, AdminSessionRepository sessions, AuditService audit,
                            AdminTwoFactorService twoFactor,
                            @Value("${superadmin.username:superadmin}") String superUsername,
                            @Value("${superadmin.password:}") String superPassword,
                            @Value("${superadmin.allowed-ips:}") String superAllowedIps) {
        this.staff = staff;
        this.sessions = sessions;
        this.audit = audit;
        this.twoFactor = twoFactor;
        this.superUsername = superUsername;
        this.superPassword = superPassword;
        this.superIps = new IpAllowList(superAllowedIps);
        if (!superIps.invalidEntries().isEmpty()) {
            log.warn("SUPERADMIN_ALLOWED_IPS da noto'g'ri qoidalar e'tiborsiz qoldirildi: {}", superIps.invalidEntries());
        }
        if (superIps.enabled()) log.info("Super admin uchun IP cheklovi yoqilgan");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Oldingi versiyada servis admini CENTER_ADMIN edi — endi u DIRECTOR
        @SuppressWarnings("deprecation")
        int migrated = staff.migrateRole(Role.CENTER_ADMIN, Role.DIRECTOR);
        if (migrated > 0) log.info("{} ta servis admini DIRECTOR roliga o'tkazildi", migrated);

        if (staff.existsByRole(Role.SUPER_ADMIN)) return;
        String password = superPassword;
        if (password == null || password.isBlank()) {
            password = Formats.newToken().substring(0, 12);
            log.warn("""

                    ==================================================
                     SUPER ADMIN yaratildi
                     Login:  {}
                     Parol:  {}
                     Kirgandan keyin parolni o'zgartirishni unutmang
                     (yoki SUPERADMIN_PASSWORD ni sozlang).
                    ==================================================""", superUsername, password);
        }
        StaffUser u = new StaffUser();
        u.setUsername(superUsername.toLowerCase());
        u.setFullName("Super admin");
        u.setPasswordHash(encoder.encode(password));
        u.setRole(Role.SUPER_ADMIN);
        staff.save(u);
    }

    public String hash(String password) {
        return encoder.encode(password);
    }

    /**
     * 1-bosqich: login + parol. Super admin uchun Telegram 2FA yoqilgan bo'lsa, sessiya o'rniga
     * challenge qaytadi va Telegram'ga 6 xonali kod ketadi (2-bosqich: {@link #verifyCode}).
     */
    @Transactional
    public LoginResult login(String username, String password, String ip) {
        StaffUser u = staff.findByUsername(username == null ? "" : username.trim().toLowerCase()).orElse(null);
        if (u == null || password == null || !encoder.matches(password, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, I18n.t("err.auth.badCredentials"));
        }
        checkAllowed(u);
        checkSuperIp(u, ip);
        u.setLang(LangContext.get()); // panelda tanlangan til — 2FA va boshqa Telegram xabarlari shu tilda
        if (twoFactor.required(u)) {
            return LoginResult.pending(twoFactor.startChallenge(u, ip));
        }
        return LoginResult.ok(openSession(u, "Tizimga kirdi"), toPrincipal(u));
    }

    /** 2-bosqich: Telegram'dan kelgan 6 xonali kod. */
    @Transactional
    public LoginResult verifyCode(String challenge, String code, String ip) {
        StaffUser u = twoFactor.verify(challenge, code);
        checkAllowed(u);
        checkSuperIp(u, ip);
        return LoginResult.ok(openSession(u, "Tizimga kirdi (Telegram kod bilan)"), toPrincipal(u));
    }

    private String openSession(StaffUser u, String auditText) {
        AdminSession s = new AdminSession();
        s.setToken(Formats.newToken() + Formats.newToken());
        s.setStaff(u);
        s.setExpiresAt(Instant.now().plus(SESSION_TTL));
        sessions.save(s);
        audit.staff(toPrincipal(u), u.getCenter(), auditText);
        return s.getToken();
    }

    // ---------- Super admin: 2FA holati ----------

    @Transactional(readOnly = true)
    public TwoFactorStatus twoFactorStatus(Long staffId) {
        StaffUser u = staff.findById(staffId).orElseThrow(AdminAuthService::unauthorized);
        boolean enabled = u.getTelegramChatId() != null;
        return new TwoFactorStatus(enabled, twoFactor.required(u), enabled ? null : twoFactor.linkUrl(u), superIps.enabled());
    }

    /** 2FA ni o'chirish — parol bilan tasdiqlanadi (sessiya o'g'irlangan bo'lsa ham o'chira olmasin). */
    @Transactional
    public void disableTwoFactor(Long staffId, String password) {
        StaffUser u = staff.findById(staffId).orElseThrow(AdminAuthService::unauthorized);
        if (password == null || !encoder.matches(password, u.getPasswordHash())) {
            throw ApiException.badRequest(I18n.t("err.auth.wrongPassword"));
        }
        twoFactor.unlink(u);
    }

    /** Super admin uchun IP cheklovi (SUPERADMIN_ALLOWED_IPS). Boshqa rollarga tegmaydi. */
    private void checkSuperIp(StaffUser u, String ip) {
        if (u.getRole() != Role.SUPER_ADMIN || superIps.allows(ip)) return;
        audit.system(u.displayName(), u.getRole().labelUz(), null, "Super admin kirishi ruxsat etilmagan IP dan rad etildi: " + ip);
        log.warn("Super admin kirishi ruxsat etilmagan IP dan rad etildi: {}", ip);
        throw forbidden(I18n.t("err.auth.ipDenied", ip));
    }

    /** Joriy HTTP so'rovning IP manzili (Nginx orqasida forward-headers-strategy=native real IP ni beradi). */
    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            return a.getRequest().getRemoteAddr();
        }
        return null;
    }

    @Transactional
    public void logout(String token) {
        if (token != null) sessions.deleteByToken(token);
    }

    /** "Authorization: Bearer ..." sarlavhasidan xodimni aniqlaydi. */
    @Transactional(readOnly = true)
    public AdminPrincipal require(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : null;
        if (token == null || token.isEmpty()) throw unauthorized();
        AdminSession s = sessions.findWithStaff(token).orElseThrow(AdminAuthService::unauthorized);
        if (s.getExpiresAt().isBefore(Instant.now())) throw unauthorized();
        StaffUser u = s.getStaff();
        checkAllowed(u);
        return toPrincipal(u);
    }

    public AdminPrincipal requireSuper(String authHeader) {
        AdminPrincipal p = require(authHeader);
        if (!p.isSuper()) throw forbidden(I18n.t("err.auth.superOnly"));
        if (superIps.enabled()) {
            String ip = currentIp();
            if (!superIps.allows(ip)) throw forbidden(I18n.t("err.auth.ipDenied", ip));
        }
        return p;
    }

    /** Istalgan servis xodimi: direktor, administrator, usta. */
    public AdminPrincipal requireCenter(String authHeader) {
        AdminPrincipal p = require(authHeader);
        if (p.centerId() == null) throw forbidden(I18n.t("err.auth.centerOnly"));
        return p;
    }

    /** Direktor yoki administrator: mijozlar bilan aloqa. */
    public AdminPrincipal requireContact(String authHeader) {
        AdminPrincipal p = requireCenter(authHeader);
        if (!p.canContactCustomers()) throw forbidden(I18n.t("err.auth.contactOnly"));
        return p;
    }

    public AdminPrincipal requireDirector(String authHeader) {
        AdminPrincipal p = requireCenter(authHeader);
        if (!p.isDirector()) throw forbidden(I18n.t("err.auth.directorOnly"));
        return p;
    }

    @Transactional
    public void changePassword(Long staffId, String oldPassword, String newPassword) {
        StaffUser u = staff.findById(staffId).orElseThrow(AdminAuthService::unauthorized);
        if (oldPassword == null || !encoder.matches(oldPassword, u.getPasswordHash())) {
            throw ApiException.badRequest(I18n.t("err.auth.currentPassword"));
        }
        validatePassword(newPassword);
        u.setPasswordHash(encoder.encode(newPassword));
        audit.staff(toPrincipal(u), u.getCenter(), "O'z parolini o'zgartirdi");
    }

    public static void validatePassword(String p) {
        if (p == null || p.length() < 8) throw ApiException.badRequest(I18n.t("err.auth.passwordShort"));
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanupExpired() {
        sessions.deleteExpired(Instant.now());
    }

    public AdminPrincipal toPrincipal(StaffUser u) {
        return new AdminPrincipal(u.getId(), u.getUsername(), u.displayName(), u.getRole(),
                u.getCenter() == null ? null : u.getCenter().getId(),
                u.getCenter() == null ? null : u.getCenter().getName());
    }

    private void checkAllowed(StaffUser u) {
        if (u.isBlocked()) throw forbidden(I18n.t("err.auth.blocked"));
        if (u.getRole().isCenterRole() && (u.getCenter() == null || !u.getCenter().isActive())) {
            throw forbidden(I18n.t("err.auth.centerDisabled"));
        }
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, I18n.t("err.auth.loginAgain"));
    }

    private static ApiException forbidden(String msg) {
        return new ApiException(HttpStatus.FORBIDDEN, msg);
    }

    /**
     * Kirish javobi. twoFactor=true bo'lsa token yo'q: brauzer challenge + Telegram'dagi kodni
     * /api/admin/login/verify ga yuboradi.
     */
    public record LoginResult(String token, AdminPrincipal user, boolean twoFactor, String challenge) {
        static LoginResult ok(String token, AdminPrincipal user) {
            return new LoginResult(token, user, false, null);
        }

        static LoginResult pending(String challenge) {
            return new LoginResult(null, null, true, challenge);
        }
    }

    /** Super admin paneli uchun 2FA holati. linkUrl — Telegram'ga ulash havolasi (yoqilgan bo'lsa null). */
    public record TwoFactorStatus(boolean enabled, boolean active, String linkUrl, boolean ipRestricted) {
    }
}
