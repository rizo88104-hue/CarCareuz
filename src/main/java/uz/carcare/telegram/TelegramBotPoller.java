package uz.carcare.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.carcare.admin.AdminTwoFactorService;
import uz.carcare.config.AppProperties;
import uz.carcare.i18n.I18n;
import uz.carcare.i18n.Lang;
import uz.carcare.i18n.LangContext;
import uz.carcare.model.Car;
import uz.carcare.reminder.MileageService;
import uz.carcare.util.RateLimiter;
import uz.carcare.web.ApiException;
import uz.carcare.web.CarService;
import uz.carcare.web.CustomerLoginService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static uz.carcare.util.Formats.html;
import static uz.carcare.util.Formats.prettyPlate;

/**
 * Bitta bot hamma foydalanuvchilarga xizmat qiladi (long polling).
 *
 * Til: chatga ulangan mashinaning tili (o'zbek/rus); ulanmagan chat uchun Telegram'dagi til.
 * /uz va /ru buyruqlari (yoki /lang tugmalari) tilni almashtiradi.
 *
 * Spamdan himoya:
 *  - har bir foydalanuvchi 1 daqiqada ko'pi bilan 15 ta xabar yubora oladi,
 *    undan ortig'i javobsiz tashlab yuboriladi (bot ishlashda davom etadi);
 *  - ogohlantirish bir daqiqada faqat bir marta yuboriladi;
 *  - guruh va kanallardagi xabarlar e'tiborsiz qoldiriladi.
 *
 * Mashinani ulash (bepul tasdiqlash, SMS shart emas):
 *   1. Sahifadagi tugma yoki chekdagi QR → /start TOKEN
 *   2. Bot "📱 Raqamni yuborish" tugmasini ko'rsatadi
 *   3. Telegram'dagi raqam ro'yxatdan o'tgan raqamga mos kelsa — ulanadi
 */
@Component
public class TelegramBotPoller {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotPoller.class);

    private final TelegramClient telegram;
    private final CarService cars;
    private final CustomerLoginService login;
    private final AdminTwoFactorService adminTwoFactor;
    private final MileageService mileage;
    private final AppProperties app;

    private final RateLimiter perUser = new RateLimiter(15, 60_000);
    private final RateLimiter spamWarning = new RateLimiter(1, 60_000);
    /** chatId → raqam tasdiqlanishini kutayotgan mashina tokeni */
    private final Map<Long, String> pendingLinks = new ConcurrentHashMap<>();

    private long offset = 0;
    private boolean initialized = false;

    public TelegramBotPoller(TelegramClient telegram, CarService cars, CustomerLoginService login,
                             AdminTwoFactorService adminTwoFactor, MileageService mileage, AppProperties app) {
        this.telegram = telegram;
        this.cars = cars;
        this.login = login;
        this.adminTwoFactor = adminTwoFactor;
        this.mileage = mileage;
        this.app = app;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void poll() {
        if (!telegram.enabled()) return;
        if (!initialized) {
            telegram.deleteWebhook();
            initialized = true;
            log.info("Telegram bot ishga tushdi");
        }
        try {
            JsonNode res = telegram.getUpdates(offset, 25);
            if (res == null || !res.path("ok").asBoolean(false)) return;
            for (JsonNode upd : res.path("result")) {
                offset = upd.path("update_id").asLong() + 1;
                try {
                    if (upd.has("callback_query")) handleCallback(upd.path("callback_query"));
                    else handle(upd.path("message"));
                } catch (Exception e) {
                    log.error("Bot xabarini qayta ishlashda xato", e);
                } finally {
                    LangContext.clear();
                }
            }
        } catch (Exception e) {
            log.warn("getUpdates xatosi: {}", e.getMessage());
            sleepQuietly(5000);
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        perUser.cleanup();
        spamWarning.cleanup();
        if (pendingLinks.size() > 10_000) pendingLinks.clear(); // xotira to'lib ketmasligi uchun
    }

    /** Chat tili: ulangan mashinaning tili, bo'lmasa Telegram profilidagi til. */
    private Lang langOf(long chatId, JsonNode from) {
        List<Car> list = cars.carsOf(chatId);
        if (!list.isEmpty()) return list.get(0).lang();
        return Lang.fromTelegram(from.path("language_code").asText(""));
    }

    private void handle(JsonNode msg) {
        if (msg.isMissingNode()) return;
        if (!"private".equals(msg.path("chat").path("type").asText())) return; // guruhlar e'tiborsiz

        long chatId = msg.path("chat").path("id").asLong();
        JsonNode from = msg.path("from");
        LangContext.set(langOf(chatId, from));

        // ---- Spam himoyasi ----
        if (!perUser.tryAcquire(String.valueOf(chatId))) {
            if (spamWarning.tryAcquire(String.valueOf(chatId))) {
                telegram.sendMessage(chatId, I18n.t("bot.tooMany"));
            }
            return;
        }

        if (msg.has("contact")) {
            handleContact(chatId, msg);
            return;
        }
        if (!msg.has("text")) return;

        String text = msg.path("text").asText("").trim();
        String name = from.path("first_name").asText("");

        if (text.startsWith("/start")) {
            String token = text.length() > 6 ? text.substring(6).trim() : "";
            if (token.isEmpty()) {
                telegram.sendMessage(chatId, welcome(name), langKeyboard());
                return;
            }
            // Super admin 2FA ni ulash: /start adm_KOD (panelda «Telegram'ga ulash» tugmasidan)
            if (token.startsWith("adm_")) {
                telegram.sendMessage(chatId, adminTwoFactor.link(token.substring(4), chatId), TelegramClient.removeKeyboard());
                return;
            }
            String plate = cars.plateByToken(token);
            if (plate == null) {
                telegram.sendMessage(chatId, I18n.t("bot.badLink"));
                return;
            }
            pendingLinks.put(chatId, token);
            telegram.sendMessage(chatId, I18n.t("bot.confirmPhone", html(prettyPlate(plate))), TelegramClient.contactKeyboard());
        } else if (text.startsWith("/holat") || text.startsWith("/status")) {
            telegram.sendMessage(chatId, cars.statusText(chatId));
        } else if (text.startsWith("/ball") || text.startsWith("/bonus") || text.startsWith("/karta")) {
            telegram.sendMessage(chatId, cars.loyaltyText(chatId));
        } else if (text.startsWith("/probeg") || text.startsWith("/km") || MileageService.parseKm(text) != null) {
            // "/probeg 123456", faqat "123456" yoki "01A123BC 123456" — probegni yangilash
            telegram.sendMessage(chatId, mileage.fromTelegram(chatId, text));
        } else if (text.startsWith("/ru") || text.startsWith("/uz")) {
            switchLang(chatId, text.startsWith("/ru") ? Lang.RU : Lang.UZ);
        } else if (text.startsWith("/lang") || text.startsWith("/til") || text.startsWith("/язык")) {
            telegram.sendMessage(chatId, I18n.t("bot.chooseLang"), langKeyboard());
        } else {
            telegram.sendMessage(chatId, welcome(name), langKeyboard());
        }
    }

    /** Tilni almashtirish: ulangan mashinalar uchun saqlanadi, ulanmagan chatga faqat javob tilida. */
    private void switchLang(long chatId, Lang lang) {
        int n = cars.setLang(chatId, lang);
        LangContext.set(lang);
        telegram.sendMessage(chatId, I18n.t("bot.langSet") + (n == 0 ? "\n" + I18n.t("bot.langNotLinked") : ""),
                TelegramClient.removeKeyboard());
    }

    private static Map<String, Object> langKeyboard() {
        return TelegramClient.inlineKeyboard(new Object[][]{
                {TelegramClient.button("🇺🇿 O'zbekcha", "lang:uz"), TelegramClient.button("🇷🇺 Русский", "lang:ru")}
        });
    }

    /**
     * Inline tugmalar: kirish so'rovini tasdiqlash / rad etish, rad etgandan keyin havolani yangilash, til.
     *   lg:ok:KOD — «Ha, bu men»,  lg:no:KOD — «Yo'q»,  rt:KOD — «Havolani yangilash»,  lang:uz|ru — til.
     */
    private void handleCallback(JsonNode cq) {
        String callbackId = cq.path("id").asText("");
        JsonNode msg = cq.path("message");
        long chatId = msg.path("chat").path("id").asLong();
        long messageId = msg.path("message_id").asLong();
        String data = cq.path("data").asText("");
        if (chatId == 0 || !"private".equals(msg.path("chat").path("type").asText())) {
            telegram.answerCallbackQuery(callbackId, null, false);
            return;
        }
        LangContext.set(langOf(chatId, cq.path("from")));
        if (!perUser.tryAcquire(String.valueOf(chatId))) {
            telegram.answerCallbackQuery(callbackId, I18n.t("bot.tooMany"), false);
            return;
        }
        try {
            if (data.startsWith("lang:")) {
                Lang lang = Lang.of(data.substring(5));
                int n = cars.setLang(chatId, lang);
                LangContext.set(lang);
                telegram.answerCallbackQuery(callbackId, null, false);
                telegram.editMessageText(chatId, messageId,
                        I18n.t("bot.langSet") + (n == 0 ? "\n" + I18n.t("bot.langNotLinked") : "") + "\n\n" + welcome(cq.path("from").path("first_name").asText("")), null);
            } else if (data.startsWith("lg:ok:")) {
                String text = login.resolve(data.substring(6), chatId, true);
                telegram.answerCallbackQuery(callbackId, null, false);
                telegram.editMessageText(chatId, messageId, text, null);
            } else if (data.startsWith("lg:no:")) {
                String code = data.substring(6);
                String text = login.resolve(code, chatId, false);
                telegram.answerCallbackQuery(callbackId, null, false);
                Map<String, Object> keyboard = text.startsWith("🚫")
                        ? TelegramClient.inlineKeyboard(new Object[][]{{TelegramClient.button(I18n.t("bot.btn.rotate"), "rt:" + code)}})
                        : null;
                telegram.editMessageText(chatId, messageId, text, keyboard);
            } else if (data.startsWith("rt:")) {
                String text = login.rotateFromTelegram(data.substring(3), chatId);
                telegram.answerCallbackQuery(callbackId, null, false);
                telegram.editMessageText(chatId, messageId, text, null);
            } else {
                telegram.answerCallbackQuery(callbackId, null, false);
            }
        } catch (ApiException e) {
            telegram.answerCallbackQuery(callbackId, "❌ " + e.getMessage(), true);
        }
    }

    private void handleContact(long chatId, JsonNode msg) {
        String token = pendingLinks.get(chatId);
        if (token == null) {
            telegram.sendMessage(chatId, I18n.t("bot.pressLinkFirst"), TelegramClient.removeKeyboard());
            return;
        }
        JsonNode contact = msg.path("contact");
        // Faqat o'z raqamini yuborishi kerak (boshqa odamning kontaktini emas)
        if (contact.path("user_id").asLong() != msg.path("from").path("id").asLong()) {
            telegram.sendMessage(chatId, I18n.t("bot.ownContactOnly"), TelegramClient.contactKeyboard());
            return;
        }
        try {
            Lang tgLang = Lang.fromTelegram(msg.path("from").path("language_code").asText(""));
            Car car = cars.linkTelegram(token, chatId, contact.path("phone_number").asText(""), tgLang);
            pendingLinks.remove(chatId);
            LangContext.set(car.lang());
            telegram.sendMessage(chatId,
                    I18n.t("bot.linked", html(prettyPlate(car.getPlateNumber())), app.reminderDaysBefore()) + "\n\n" + cars.statusText(chatId),
                    TelegramClient.removeKeyboard());
        } catch (ApiException e) {
            pendingLinks.remove(chatId);
            telegram.sendMessage(chatId, "❌ " + html(e.getMessage()), TelegramClient.removeKeyboard());
        }
    }

    private String welcome(String name) {
        return I18n.t("bot.welcome", name.isBlank() ? "" : ", " + html(name), app.baseUrl());
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
