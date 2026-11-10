package uz.carcare.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.config.TelegramProperties;
import uz.carcare.model.AdminLoginCode;
import uz.carcare.model.StaffUser;
import uz.carcare.model.StaffUser.Role;
import uz.carcare.repo.AdminLoginCodeRepository;
import uz.carcare.repo.StaffUserRepository;
import uz.carcare.telegram.TelegramClient;
import uz.carcare.util.Formats;
import uz.carcare.web.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static uz.carcare.util.Formats.html;

/**
 * Super admin uchun ikki bosqichli tasdiq (2FA) Telegram orqali.
 *
 *  Ulash:   panelda «Telegram'ga ulash» → t.me/Bot?start=adm_KOD → bot chatni super adminga bog'laydi.
 *  Kirish:  login + parol to'g'ri → Telegram'ga 6 xonali kod → kod kiritiladi → sessiya beriladi.
 *  Kod 5 daqiqa amal qiladi, 5 ta noto'g'ri urinishdan keyin yonib ketadi, bir marta ishlatiladi.
 *  Tiklash: Telegram'ga kirish yo'qolsa, bazada staff_users.telegram_chat_id ni null qilish kifoya.
 */
@Service
public class AdminTwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(AdminTwoFactorService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    /** Bir admin uchun 10 daqiqada ko'pi bilan shuncha kod (Telegram'ni spam qilmaslik uchun). */
    private static final int MAX_CODES_PER_WINDOW = 5;
    private static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminLoginCodeRepository codes;
    private final StaffUserRepository staff;
    private final TelegramClient telegram;
    private final TelegramProperties telegramProps;
    private final AuditService audit;

    /** Ulash kodi → (xodim id, muddati). Xotirada: ulash bir necha daqiqada tugaydi. */
    private final Map<String, LinkTicket> linkTickets = new ConcurrentHashMap<>();

    private record LinkTicket(Long staffId, Instant expiresAt) {
    }

    public AdminTwoFactorService(AdminLoginCodeRepository codes, StaffUserRepository staff,
                                 TelegramClient telegram, TelegramProperties telegramProps, AuditService audit) {
        this.codes = codes;
        this.staff = staff;
        this.telegram = telegram;
        this.telegramProps = telegramProps;
        this.audit = audit;
    }

    /** Bu xodim uchun kirishda kod talab qilinadimi. */
    public boolean required(StaffUser u) {
        return u.getRole() == Role.SUPER_ADMIN && u.getTelegramChatId() != null && telegram.enabled();
    }

    /** Parol to'g'ri kiritilgach: kod yaratib Telegram'ga yuboradi, brauzerga challenge qaytaradi. */
    @Transactional
    public String startChallenge(StaffUser u, String ip) {
        Instant now = Instant.now();
        if (codes.countByStaffAndCreatedAtAfter(u, now.minus(Duration.ofMinutes(10))) >= MAX_CODES_PER_WINDOW) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Kod juda ko'p marta so'raldi. 10 daqiqadan keyin qayta urinib ko'ring.");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String challenge = Formats.newToken() + Formats.newToken();
        codes.save(new AdminLoginCode(u, challenge, sha256(code), ip, now.plus(CODE_TTL)));

        String text = "🔐 <b>Admin panelga kirish kodi</b>\n\n"
                + "<code>" + code + "</code>\n\n"
                + "Kod " + CODE_TTL.toMinutes() + " daqiqa amal qiladi va bir marta ishlaydi.\n"
                + "Kirishga urinilgan IP: <code>" + html(ip == null ? "?" : ip) + "</code>\n\n"
                + "⚠️ Agar bu siz bo'lmasangiz — kimdir parolingizni bilib olgan. "
                + "Kodni hech kimga bermang va parolni darhol o'zgartiring.";
        if (!telegram.sendMessage(u.getTelegramChatId(), text)) {
            throw ApiException.badRequest("Telegram'ga kod yuborilmadi. Botni bloklagan bo'lsangiz, uni oching va qayta urinib ko'ring.");
        }
        audit.system(u.displayName(), u.getRole().label(), null, "Kirish: Telegram'ga tasdiqlash kodi yuborildi (IP " + ip + ")");
        return challenge;
    }

    /** Kodni tekshiradi. To'g'ri bo'lsa xodimni qaytaradi (sessiyani chaqiruvchi yaratadi). */
    @Transactional
    public StaffUser verify(String challenge, String code) {
        AdminLoginCode c = codes.findByChallenge(challenge == null ? "" : challenge.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Kirish muddati o'tdi. Login va parolni qayta kiriting."));
        Instant now = Instant.now();
        if (c.isUsed() || c.isExpired(now) || c.getAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Kod eskirgan. Login va parolni qayta kiriting — yangi kod yuboriladi.");
        }
        c.setAttempts(c.getAttempts() + 1);
        String digits = code == null ? "" : code.replaceAll("\\D", "");
        if (digits.length() != 6 || !constantTimeEquals(sha256(digits), c.getCodeHash())) {
            int left = MAX_ATTEMPTS - c.getAttempts();
            StaffUser u = c.getStaff();
            audit.system(u.displayName(), u.getRole().label(), null, "Kirish: noto'g'ri tasdiqlash kodi (IP " + c.getIp() + ")");
            throw new ApiException(HttpStatus.UNAUTHORIZED, left > 0
                    ? "Kod noto'g'ri. Yana " + left + " ta urinish qoldi."
                    : "Kod noto'g'ri. Urinishlar tugadi — login va parolni qayta kiriting.");
        }
        c.setUsed(true);
        return c.getStaff();
    }

    // ---------- Telegram'ni ulash ----------

    /** Panel uchun: ulash havolasi (t.me/Bot?start=adm_KOD). Bot sozlanmagan bo'lsa null. */
    public String linkUrl(StaffUser u) {
        if (!telegram.enabled() || telegramProps.botUrl() == null) return null;
        String code = Formats.newToken();
        linkTickets.put(code, new LinkTicket(u.getId(), Instant.now().plus(LINK_TTL)));
        return telegramProps.botUrl() + "?start=adm_" + code;
    }

    /** Botdan: /start adm_KOD. Chatni super adminga bog'laydi, foydalanuvchiga ko'rsatiladigan matnni qaytaradi. */
    @Transactional
    public String link(String code, long chatId) {
        LinkTicket t = linkTickets.remove(code);
        if (t == null || Instant.now().isAfter(t.expiresAt())) {
            return "❌ Ulash havolasi eskirgan. Admin panelda «Telegram'ga ulash» tugmasini qayta bosing.";
        }
        StaffUser u = staff.findById(t.staffId()).orElse(null);
        if (u == null || u.getRole() != Role.SUPER_ADMIN) return "❌ Xodim topilmadi.";
        u.setTelegramChatId(chatId);
        audit.system(u.displayName(), u.getRole().label(), null, "Ikki bosqichli tasdiq (Telegram) yoqildi");
        log.info("Super admin {} uchun Telegram 2FA ulandi", u.getUsername());
        return "✅ <b>Ikki bosqichli tasdiq yoqildi.</b>\n\n"
                + "Endi admin panelga kirishda login va paroldan keyin shu yerga 6 xonali kod keladi. "
                + "Kodni hech kimga bermang.";
    }

    /** Panel uchun: 2FA ni o'chirish (parol tekshiruvi chaqiruvchida). */
    @Transactional
    public void unlink(StaffUser u) {
        if (u.getTelegramChatId() == null) return;
        Long chat = u.getTelegramChatId();
        u.setTelegramChatId(null);
        audit.system(u.displayName(), u.getRole().label(), null, "Ikki bosqichli tasdiq (Telegram) o'chirildi");
        telegram.sendLater(chat, "⚠️ Admin panel uchun ikki bosqichli tasdiq <b>o'chirildi</b>. "
                + "Bu siz bo'lmasangiz, parolni darhol o'zgartiring.");
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 900_000)
    @Transactional
    public void cleanup() {
        try {
            codes.deleteOlderThan(Instant.now().minus(Duration.ofDays(1)));
        } catch (Exception e) {
            log.warn("Kirish kodlarini tozalab bo'lmadi: {}", e.getMessage());
        }
        Instant now = Instant.now();
        linkTickets.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    // ---------- Yordamchi ----------

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
