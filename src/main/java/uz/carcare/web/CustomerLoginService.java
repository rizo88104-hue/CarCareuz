package uz.carcare.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.admin.AuditService;
import uz.carcare.config.AppProperties;
import uz.carcare.dto.Dtos.LoginStatusDto;
import uz.carcare.model.Car;
import uz.carcare.model.LoginRequest;
import uz.carcare.model.LoginRequest.Status;
import uz.carcare.repo.LoginRequestRepository;
import uz.carcare.telegram.TelegramClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static uz.carcare.util.Formats.*;

/**
 * Mijoz sahifasiga kirish xavfsizligi.
 *
 * 1. Qayta kirishni Telegram orqali tasdiqlash. Telefon + mashina raqamini bilgan har kim
 *    sahifaga kira olmasligi uchun: mashina botga ulangan bo'lsa, egasiga Telegram'da
 *    «Ha, bu men / Yo'q» tugmalari yuboriladi. Havola faqat «Ha» bosilganda beriladi.
 *    Telegram ulanmagan bo'lsa (tasdiqlash kanali yo'q) — eskidek darhol kiradi.
 *
 * 2. Havolani yangilash. Mashina sahifasining maxfiy kaliti almashtiriladi, eski havola
 *    shu zahoti ishlamay qoladi. Sahifadagi tugma yoki botdagi tugma orqali qilinadi.
 */
@Service
public class CustomerLoginService {

    private static final Logger log = LoggerFactory.getLogger(CustomerLoginService.class);

    /** Tasdiqlash so'rovi shuncha vaqt amal qiladi. */
    static final Duration TTL = Duration.ofMinutes(5);
    /** Bir mashina uchun 10 daqiqada ko'pi bilan shuncha so'rov (egasini spam qilmaslik uchun). */
    private static final int MAX_PER_WINDOW = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final LoginRequestRepository requests;
    private final TelegramClient telegram;
    private final AppProperties app;
    private final AuditService audit;

    public CustomerLoginService(LoginRequestRepository requests, TelegramClient telegram,
                                AppProperties app, AuditService audit) {
        this.requests = requests;
        this.telegram = telegram;
        this.app = app;
        this.audit = audit;
    }

    /** Tasdiqlash kerakmi: mashina Telegramga ulangan va bot yoqilgan bo'lsa — ha. */
    public boolean needsConfirmation(Car car) {
        return car.getTelegramChatId() != null && telegram.enabled();
    }

    /** Egasiga Telegram'da so'rov yuboradi va brauzer kuzatib turadigan kodni qaytaradi. */
    @Transactional
    public LoginRequest start(Car car) {
        Instant now = Instant.now();
        if (requests.countByCarAndCreatedAtAfter(car, now.minus(WINDOW)) >= MAX_PER_WINDOW) {
            throw ApiException.badRequest("Tasdiqlash so'rovi allaqachon yuborilgan. Telegram botni oching va "
                    + "«Ha, bu men» tugmasini bosing, yoki 10 daqiqadan keyin qayta urinib ko'ring.");
        }
        LoginRequest r = requests.save(new LoginRequest(car, newToken(), now.plus(TTL)));

        String plate = html(prettyPlate(car.getPlateNumber()));
        String text = "🔐 <b>" + plate + "</b> sahifasiga kirish so'rovi\n\n"
                + "Kimdir saytda sizning telefon va mashina raqamingizni kiritib, mashina sahifangizga kirmoqchi.\n\n"
                + "Bu sizmisiz?\n"
                + "• Siz bo'lsangiz — <b>✅ Ha, bu men</b> ni bosing, brauzerda sahifa ochiladi.\n"
                + "• Siz bo'lmasangiz — <b>❌ Yo'q</b> ni bosing, hech kim kira olmaydi.\n\n"
                + "So'rov " + TTL.toMinutes() + " daqiqa amal qiladi.";
        Map<String, Object> keyboard = TelegramClient.inlineKeyboard(new Object[][]{
                {TelegramClient.button("✅ Ha, bu men", "lg:ok:" + r.getCode()),
                 TelegramClient.button("❌ Yo'q", "lg:no:" + r.getCode())}
        });
        if (!telegram.sendMessage(car.getTelegramChatId(), text, keyboard)) {
            r.setStatus(Status.EXPIRED);
            throw ApiException.badRequest("Telegram'ga tasdiqlash xabari yuborilmadi. Botni bloklagan bo'lsangiz, "
                    + "uni oching va qayta urinib ko'ring. Yordam kerak bo'lsa, avtoservisingizga murojaat qiling.");
        }
        return r;
    }

    /** Brauzer har 2 soniyada so'raydi. Tasdiqlangan bo'lsa havola bir marta qaytariladi. */
    @Transactional
    public LoginStatusDto poll(String code) {
        LoginRequest r = requests.findByCode(code).orElseThrow(() -> ApiException.notFound("So'rov topilmadi"));
        if (r.getStatus() == Status.PENDING && r.isExpired(Instant.now())) r.setStatus(Status.EXPIRED);
        return switch (r.getStatus()) {
            case PENDING -> new LoginStatusDto("pending", null, null);
            case APPROVED -> {
                r.setStatus(Status.USED);
                String token = r.getCar().getToken();
                yield new LoginStatusDto("approved", token, app.carUrl(token));
            }
            case DENIED -> new LoginStatusDto("denied", null, null);
            default -> new LoginStatusDto("expired", null, null);
        };
    }

    /**
     * Botdagi «Ha» / «Yo'q» tugmasi. Faqat mashina ulangan chatdan qabul qilinadi.
     * Qaytgan matn botdagi xabar o'rniga yoziladi.
     */
    @Transactional
    public String resolve(String code, long chatId, boolean approve) {
        LoginRequest r = requests.findByCode(code).orElseThrow(() -> ApiException.notFound("So'rov topilmadi"));
        Car car = r.getCar();
        if (car.getTelegramChatId() == null || car.getTelegramChatId() != chatId) {
            throw ApiException.badRequest("Bu so'rov sizning mashinangizga tegishli emas");
        }
        String plate = html(prettyPlate(car.getPlateNumber()));
        if (r.getStatus() != Status.PENDING) {
            return "🔐 <b>" + plate + "</b> — bu so'rov allaqachon yakunlangan.";
        }
        if (r.isExpired(Instant.now())) {
            r.setStatus(Status.EXPIRED);
            return "⌛ <b>" + plate + "</b> — so'rov muddati o'tdi. Saytda qayta urinib ko'ring.";
        }
        if (approve) {
            r.setStatus(Status.APPROVED);
            audit.system(car.getFullName(), "Mijoz", null, prettyPlate(car.getPlateNumber()) + " sahifasiga kirish Telegram'da tasdiqlandi");
            return "✅ <b>" + plate + "</b> — kirish tasdiqlandi. Brauzerda sahifa ochiladi.";
        }
        r.setStatus(Status.DENIED);
        audit.system(car.getFullName(), "Mijoz", null, prettyPlate(car.getPlateNumber()) + " sahifasiga kirish Telegram'da rad etildi");
        return "🚫 <b>" + plate + "</b> — kirish rad etildi, hech kim kira olmadi.\n\n"
                + "Agar sahifa havolangiz begona odamga tushgan deb o'ylasangiz, pastdagi tugma bilan uni yangilang — "
                + "eski havola darhol ishlamay qoladi.";
    }

    /** Botdagi «Havolani yangilash» tugmasi (rad etilgan so'rovdan keyin). */
    @Transactional
    public String rotateFromTelegram(String code, long chatId) {
        LoginRequest r = requests.findByCode(code).orElseThrow(() -> ApiException.notFound("So'rov topilmadi"));
        Car car = r.getCar();
        if (car.getTelegramChatId() == null || car.getTelegramChatId() != chatId) {
            throw ApiException.badRequest("Bu so'rov sizning mashinangizga tegishli emas");
        }
        newLink(car, "Telegram bot orqali");
        return "✅ <b>" + html(prettyPlate(car.getPlateNumber())) + "</b> — havola yangilandi, eski havola endi ishlamaydi.\n"
                + "Yangi havola alohida xabarda keladi.";
    }

    /**
     * Sahifa kalitini almashtiradi: eski havola darhol ishlamay qoladi. Kutilayotgan kirish
     * so'rovlari bekor qilinadi, egasiga (Telegram ulangan bo'lsa) yangi havola yuboriladi.
     */
    @Transactional
    public void newLink(Car car, String how) {
        car.setToken(newToken());
        requests.findAllByCarAndStatus(car, Status.PENDING).forEach(p -> p.setStatus(Status.EXPIRED));
        audit.system(car.getFullName(), "Mijoz", null, prettyPlate(car.getPlateNumber()) + " sahifasi havolasi yangilandi (" + how + ")");
        if (car.getTelegramChatId() != null) {
            telegram.sendLater(car.getTelegramChatId(),
                    "🔄 <b>" + html(prettyPlate(car.getPlateNumber())) + "</b> sahifangiz havolasi yangilandi.\n"
                            + "Eski havola endi ishlamaydi.\n\n"
                            + "Yangi havola:\n" + app.carUrl(car.getToken()) + "\n\n"
                            + "Havolani hech kimga bermang.");
        }
    }

    /** Eski so'rovlarni tozalash (har soatda). */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    @Transactional
    public void cleanup() {
        try {
            int n = requests.deleteOlderThan(Instant.now().minus(Duration.ofDays(1)));
            if (n > 0) log.info("{} ta eski kirish so'rovi o'chirildi", n);
        } catch (Exception e) {
            log.warn("Kirish so'rovlarini tozalab bo'lmadi: {}", e.getMessage());
        }
    }
}
