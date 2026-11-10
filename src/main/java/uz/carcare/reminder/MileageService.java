package uz.carcare.reminder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.admin.AuditService;
import uz.carcare.i18n.I18n;
import uz.carcare.model.Car;
import uz.carcare.repo.CarRepository;
import uz.carcare.web.ApiException;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uz.carcare.util.Formats.*;

/**
 * Mashinaning hozirgi probegi. Manbalar: mijoz (sahifa yoki bot), avtoservis (ish kiritganda).
 * Probeg yangilangan zahoti km bo'yicha eslatmalar tekshiriladi ({@link ReminderService#checkMileage}).
 */
@Service
public class MileageService {

    /** Haqiqiy bo'lmagan qiymatlardan himoya (odometr bu qiymatdan oshmaydi). */
    public static final int MAX_KM = 2_000_000;

    /** "123456", "123 456", "123456 km", "/probeg 123456", "01A123BC 123456" kabi matnlardan raqamni oladi. */
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d\\s]{2,8})\\s*(?:km|км)?\\s*$", Pattern.CASE_INSENSITIVE);

    private final CarRepository cars;
    private final ReminderService reminders;
    private final AuditService audit;

    public MileageService(CarRepository cars, ReminderService reminders, AuditService audit) {
        this.cars = cars;
        this.reminders = reminders;
        this.audit = audit;
    }

    /**
     * Probegni yangilaydi va km bo'yicha eslatmalarni tekshiradi.
     *
     * @param strict true — kamayishga xato beradi (mijoz o'zi kiritganda); false — kamroq qiymat
     *               indamay o'tkazib yuboriladi (masalan servis eski sanadagi ishni kiritganda)
     * @return probeg o'zgargan bo'lsa true
     */
    @Transactional
    public boolean apply(Car car, int km, String by, boolean strict) {
        if (km <= 0 || km > MAX_KM) {
            if (!strict) return false;
            throw ApiException.badRequest(I18n.t("err.km.range", String.format("%,d", MAX_KM).replace(',', ' ')));
        }
        Integer cur = car.getCurrentMileage();
        if (cur != null && km < cur) {
            if (!strict) return false;
            throw ApiException.badRequest(I18n.t("err.km.decrease", km(km), km(cur)));
        }
        if (cur != null && km == cur) {
            car.setMileageUpdatedAt(Instant.now());
            return false;
        }
        car.setCurrentMileage(km);
        car.setMileageUpdatedAt(Instant.now());
        audit.system(car.getFullName(), "Mijoz", null, prettyPlate(car.getPlateNumber()) + " probegi yangilandi: " + km(km) + " (" + by + ")");
        reminders.checkMileage(car);
        return true;
    }

    /**
     * Botdan kelgan matn: "/probeg 123456", "123456" yoki bir nechta mashina bo'lsa "01A123BC 123456".
     * Foydalanuvchiga ko'rsatiladigan HTML javob qaytaradi (tili — LangContext, poller o'rnatadi).
     */
    @Transactional
    public String fromTelegram(long chatId, String text) {
        List<Car> list = cars.findAllByTelegramChatId(chatId);
        if (list.isEmpty()) return I18n.t("bot.noCars");
        Integer km = parseKm(text);
        if (km == null) {
            StringBuilder sb = new StringBuilder(I18n.t("bot.km.title")).append("\n\n");
            for (Car c : list) sb.append("🚗 ").append(html(prettyPlate(c.getPlateNumber()))).append(" — ").append(currentText(c)).append("\n");
            sb.append("\n").append(I18n.t("bot.km.howto"));
            if (list.size() > 1) sb.append("\n").append(I18n.t("bot.km.multi", prettyPlate(list.get(0).getPlateNumber()).replace(" ", "")));
            return sb.toString();
        }
        Car car;
        if (list.size() == 1) {
            car = list.get(0);
        } else {
            car = list.stream().filter(c -> mentionsPlate(text, c.getPlateNumber())).findFirst().orElse(null);
            if (car == null) {
                StringBuilder sb = new StringBuilder(I18n.t("bot.km.which")).append("\n");
                for (Car c : list) sb.append("<code>").append(prettyPlate(c.getPlateNumber()).replace(" ", "")).append(" ").append(km).append("</code>\n");
                return sb.toString().trim();
            }
        }
        try {
            boolean changed = apply(car, km, "Telegram bot", true);
            return (changed ? "✅ " : "ℹ️ ") + I18n.t("bot.km.saved", html(prettyPlate(car.getPlateNumber())), km(km))
                    + (changed ? "" : " " + I18n.t("bot.km.unchanged")) + "\n\n" + reminders.mileageSummary(car);
        } catch (ApiException e) {
            return "❌ " + html(e.getMessage());
        }
    }

    /** Matnda probeg bormi (bot: faqat raqam yozilgan xabarlarni ham qabul qilish uchun). */
    public static Integer parseKm(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("/probeg") || t.startsWith("/km")) t = t.replaceFirst("^/\\S+", "").trim();
        Matcher m = NUMBER.matcher(t);
        if (!m.find()) return null;
        String digits = m.group(1).replaceAll("\\s", "");
        if (digits.length() < 3 || digits.length() > 7) return null;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean mentionsPlate(String text, String plate) {
        String t = text.toUpperCase().replaceAll("[\\s\\-]", "");
        return t.contains(plate);
    }

    static String currentText(Car c) {
        return c.getCurrentMileage() == null ? I18n.t("bot.km.none") : km(c.getCurrentMileage());
    }

    public static String km(int v) {
        return String.format("%,d", v).replace(',', ' ') + " km";
    }
}
