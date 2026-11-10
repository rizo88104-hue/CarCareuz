package uz.carcare.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.carcare.admin.AdminDtos.*;

import java.util.List;
import java.util.Map;

/**
 * Admin panel API. Har bir so'rov "Authorization: Bearer TOKEN" sarlavhasi bilan keladi.
 *
 *   /api/admin/login, /logout, /me, /password   — hamma xodimlar
 *   /api/admin/center/dashboard, /lookup,
 *              /records                          — direktor, administrator, usta
 *   /api/admin/center/records/{id}/ready         — direktor, administrator
 *   /api/admin/center/staff, /log                — faqat direktor
 *   /api/admin/super/**                          — super admin
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    private final AdminAuthService auth;
    private final AdminService service;
    private final SubscriptionService subscriptionService;

    public AdminController(AdminAuthService auth, AdminService service, SubscriptionService subscriptionService) {
        this.auth = auth;
        this.service = service;
        this.subscriptionService = subscriptionService;
    }

    // ---------- Kirish ----------

    @PostMapping("/login")
    public AdminAuthService.LoginResult login(@RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req.username(), req.password(), http.getRemoteAddr());
    }

    /** 2-bosqich: Telegram'ga kelgan 6 xonali kod (super admin 2FA). */
    @PostMapping("/login/verify")
    public AdminAuthService.LoginResult verify(@RequestBody LoginVerifyRequest req, HttpServletRequest http) {
        return auth.verifyCode(req.challenge(), req.code(), http.getRemoteAddr());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = AUTH, required = false) String h) {
        if (h != null && h.startsWith("Bearer ")) auth.logout(h.substring(7).trim());
    }

    @GetMapping("/me")
    public MeDto me(@RequestHeader(value = AUTH, required = false) String h) {
        AdminPrincipal p = auth.require(h);
        return new MeDto(p.username(), p.fullName(), p.role().name(), p.role().label(), p.centerName(), auth.subscriptionOf(p));
    }

    @PostMapping("/password")
    public Map<String, Boolean> password(@RequestHeader(value = AUTH, required = false) String h,
                                         @RequestBody PasswordRequest req) {
        AdminPrincipal p = auth.require(h);
        auth.changePassword(p.staffId(), req.oldPassword(), req.newPassword());
        return Map.of("ok", true);
    }

    // ---------- Servis xodimlari ----------

    @GetMapping("/center/dashboard")
    public CenterDashboard dashboard(@RequestHeader(value = AUTH, required = false) String h) {
        return service.dashboard(auth.requireCenter(h));
    }

    @GetMapping("/center/lookup")
    public CarLookupDto lookup(@RequestHeader(value = AUTH, required = false) String h, @RequestParam String plate) {
        return service.lookup(auth.requireCenter(h), plate);
    }

    @PostMapping("/center/records")
    @ResponseStatus(HttpStatus.CREATED)
    public SaveResult addRecord(@RequestHeader(value = AUTH, required = false) String h,
                                @Valid @RequestBody AdminRecordRequest req) {
        return service.addRecord(auth.requireCenter(h), req);
    }

    @PostMapping("/center/records/{id}/ready")
    public ReadyResult ready(@RequestHeader(value = AUTH, required = false) String h, @PathVariable Long id) {
        return service.sendReady(auth.requireContact(h), id);
    }

    /** Mijozni Telegramga ulash: QR kod va havolalar (ekran va chek uchun). */
    @GetMapping("/center/cars/{carId}/connect")
    public ConnectInfo connect(@RequestHeader(value = AUTH, required = false) String h, @PathVariable Long carId) {
        return service.connectInfo(auth.requireContact(h), carId);
    }

    // ---------- Direktor ----------

    @GetMapping("/center/staff")
    public List<StaffDto> staff(@RequestHeader(value = AUTH, required = false) String h) {
        return service.listStaff(auth.requireDirector(h));
    }

    @PostMapping("/center/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffDto addStaff(@RequestHeader(value = AUTH, required = false) String h, @Valid @RequestBody StaffRequest req) {
        return service.addStaff(auth.requireDirector(h), req);
    }

    @PatchMapping("/center/staff/{id}")
    public StaffDto updateStaff(@RequestHeader(value = AUTH, required = false) String h, @PathVariable Long id,
                                @RequestBody StaffUpdateRequest req) {
        return service.updateStaff(auth.requireDirector(h), id, req);
    }

    @GetMapping("/center/log")
    public List<LogDto> centerLog(@RequestHeader(value = AUTH, required = false) String h) {
        return service.centerLog(auth.requireDirector(h));
    }

    // ---------- Super admin ----------

    @GetMapping("/super/overview")
    public SuperOverview overview(@RequestHeader(value = AUTH, required = false) String h) {
        auth.requireSuper(h);
        return service.overview();
    }

    /** Ikki bosqichli tasdiq holati va Telegram'ga ulash havolasi. */
    @GetMapping("/super/2fa")
    public AdminAuthService.TwoFactorStatus twoFactor(@RequestHeader(value = AUTH, required = false) String h) {
        return auth.twoFactorStatus(auth.requireSuper(h).staffId());
    }

    /** 2FA ni o'chirish — parol bilan. */
    @PostMapping("/super/2fa/disable")
    public Map<String, Boolean> disableTwoFactor(@RequestHeader(value = AUTH, required = false) String h,
                                                 @RequestBody PasswordConfirmRequest req) {
        auth.disableTwoFactor(auth.requireSuper(h).staffId(), req.password());
        return Map.of("ok", true);
    }

    @PostMapping("/super/centers")
    @ResponseStatus(HttpStatus.CREATED)
    public CenterDto createCenter(@RequestHeader(value = AUTH, required = false) String h,
                                  @Valid @RequestBody CenterRequest req) {
        return service.createCenter(auth.requireSuper(h), req);
    }

    @PatchMapping("/super/centers/{id}")
    public CenterDto updateCenter(@RequestHeader(value = AUTH, required = false) String h,
                                  @PathVariable Long id, @RequestBody CenterUpdateRequest req) {
        return service.updateCenter(auth.requireSuper(h), id, req);
    }

    // ---------- Obuna (oylik to'lov) ----------

    @GetMapping("/super/centers/{id}/subscription")
    public SubscriptionDetail subscription(@RequestHeader(value = AUTH, required = false) String h, @PathVariable Long id) {
        auth.requireSuper(h);
        return subscriptionService.detail(id);
    }

    /** To'lov qabul qilish: muddat N oyga uzayadi (standart summa = oylar × oylik narx). */
    @PostMapping("/super/centers/{id}/subscription")
    public SubscriptionDetail paySubscription(@RequestHeader(value = AUTH, required = false) String h, @PathVariable Long id,
                                              @Valid @RequestBody SubscriptionPayRequest req) {
        return subscriptionService.pay(auth.requireSuper(h), id, req);
    }

    /** Muddatni qo'lda o'zgartirish (sinovni uzaytirish va h.k.). */
    @PatchMapping("/super/centers/{id}/subscription")
    public SubscriptionDetail setSubscription(@RequestHeader(value = AUTH, required = false) String h, @PathVariable Long id,
                                              @Valid @RequestBody SubscriptionSetRequest req) {
        return subscriptionService.setUntil(auth.requireSuper(h), id, req);
    }

    @GetMapping("/super/centers/{id}/missed")
    public List<MissedDto> missed(@RequestHeader(value = AUTH, required = false) String h, @PathVariable Long id) {
        auth.requireSuper(h);
        return service.missed(id);
    }

    @GetMapping("/super/log")
    public List<LogDto> superLog(@RequestHeader(value = AUTH, required = false) String h,
                                 @RequestParam(required = false) Long centerId) {
        auth.requireSuper(h);
        return service.superLog(centerId);
    }
}
