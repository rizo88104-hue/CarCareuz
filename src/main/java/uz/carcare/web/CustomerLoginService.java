package uz.carcare.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.admin.AuditService;
import uz.carcare.config.AppProperties;
import uz.carcare.dto.Dtos.LoginStatusDto;
import uz.carcare.i18n.I18n;
import uz.carcare.i18n.Lang;
import uz.carcare.i18n.LangContext;
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
 *
 * Telegram xabarlari mijozning tilida (Car.lang).
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
            throw ApiException.badRequest(I18n.t("err.login.tooMany"));
        }
        LoginRequest r = requests.save(new LoginRequest(car, newToken(), now.plus(TTL)));

        Lang lang = car.lang();
        String plate = html(prettyPlate(car.getPlateNumber()));
        String text = I18n.t(lang, "bot.login.request", plate, TTL.toMinutes());
        Map<String, Object> keyboard = TelegramClient.inlineKeyboard(new Object[][]{
                {TelegramClient.button(I18n.t(lang, "bot.btn.yesMe"), "lg:ok:" + r.getCode()),
                 TelegramClient.button(I18n.t(lang, "bot.btn.no"), "lg:no:" + r.getCode())}
        });
        if (!telegram.sendMessage(car.getTelegramChatId(), text, keyboard)) {
            r.setStatus(Status.EXPIRED);
            throw ApiException.badRequest(I18n.t("err.login.notSent"));
        }
        return r;
    }

    /** Brauzer har 2 soniyada so'raydi. Tasdiqlangan bo'lsa havola bir marta qaytariladi. */
    @Transactional
    public LoginStatusDto poll(String code) {
        LoginRequest r = requests.findByCode(code).orElseThrow(() -> ApiException.notFound(I18n.t("err.login.notFound")));
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
     * Qaytgan matn botdagi xabar o'rniga yoziladi (mijoz tilida).
     */
    @Transactional
    public String resolve(String code, long chatId, boolean approve) {
        LoginRequest r = requests.findByCode(code).orElseThrow(() -> ApiException.notFound(I18n.t("err.login.notFound")));
        Car car = r.getCar();
        if (car.getTelegramChatId() == null || car.getTelegramChatId() != chatId) {
            throw ApiException.badRequest(I18n.t("err.login.notYours"));
        }
        return LangContext.with(car.lang(), () -> {
            String plate = html(prettyPlate(car.getPlateNumber()));
            if (r.getStatus() != Status.PENDING) return I18n.t("bot.login.done", plate);
            if (r.isExpired(Instant.now())) {
                r.setStatus(Status.EXPIRED);
                return I18n.t("bot.login.expired", plate);
            }
            if (approve) {
                r.setStatus(Status.APPROVED);
                audit.system(car.getFullName(), "Mijoz", null, prettyPlate(car.getPlateNumber()) + " sahifasiga kirish Telegram'da tasdiqlandi");
                return I18n.t("bot.login.approved", plate);
            }
            r.setStatus(Status.DENIED);
            audit.system(car.getFullName(), "Mijoz", null, prettyPlate(car.getPlateNumber()) + " sahifasiga kirish Telegram'da rad etildi");
            return I18n.t("bot.login.denied", plate); // "🚫" bilan boshlanadi — poller shunga qarab tugma qo'shadi
        });
    }

    /** Botdagi «Havolani yangilash» tugmasi (rad etilgan so'rovdan keyin). */
    @Transactional
    public String rotateFromTelegram(String code, long chatId) {
        LoginRequest r = requests.findByCode(code).orElseThrow(() -> ApiException.notFound(I18n.t("err.login.notFound")));
        Car car = r.getCar();
        if (car.getTelegramChatId() == null || car.getTelegramChatId() != chatId) {
            throw ApiException.badRequest(I18n.t("err.login.notYours"));
        }
        newLink(car, "Telegram bot orqali");
        return I18n.t(car.lang(), "bot.rotate.done", html(prettyPlate(car.getPlateNumber())));
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
                    I18n.t(car.lang(), "bot.rotate.newLink", html(prettyPlate(car.getPlateNumber())), app.carUrl(car.getToken())));
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
