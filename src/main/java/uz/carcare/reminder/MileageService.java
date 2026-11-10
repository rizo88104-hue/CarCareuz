package uz.carcare.reminder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.admin.AuditService;
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
            throw ApiException.badRequest("Probeg 1 dan " + String.format("%,d", MAX_KM).replace(',', ' ') + " km gacha bo'lishi kerak");
        }
        Integer cur = car.getCurrentMileage();
        if (cur != null && km < cur) {
            if (!strict) return false;
            throw ApiException.badRequest("Yangi probeg (" + km(km) + ") oldingisidan (" + km(cur) + ") kam bo'lishi mumkin emas. "
                    + "Oldingi qiymat xato bo'lsa, avtoservisingizga murojaat qiling.");
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
     * Foydalanuvchiga ko'rsatiladigan HTML javob qaytaradi.
     */
    @Transactional
    public String fromTelegram(long chatId, String text) {
        List<Car> list = cars.findAllByTelegramChatId(chatId);
        if (list.isEmpty()) {
            return "Sizda ulangan mashina yo'q. Saytda ro'yxatdan o'tib, sahifadagi <b>\"Telegramga ulash\"</b> tugmasini bosing.";
        }
        Integer km = parseKm(text);
        if (km == null) {
            StringBuilder sb = new StringBuilder("🛣 <b>Probeg</b>\n\n");
            for (Car c : list) sb.append("🚗 ").append(html(prettyPlate(c.getPlateNumber()))).append(" — ").append(currentText(c)).append("\n");
            sb.append("\nYangilash uchun odometrdagi raqamni yozing, masalan: <code>123456</code>");
            if (list.size() > 1) sb.append("\nBir nechta mashina bo'lgani uchun raqam bilan: <code>")
                    .append(prettyPlate(list.get(0).getPlateNumber()).replace(" ", "")).append(" 123456</code>");
            return sb.toString();
        }
        Car car;
        if (list.size() == 1) {
            car = list.get(0);
        } else {
            car = list.stream().filter(c -> mentionsPlate(text, c.getPlateNumber())).findFirst().orElse(null);
            if (car == null) {
                StringBuilder sb = new StringBuilder("Sizda bir nechta mashina bor. Qaysi biri? Raqam bilan yozing:\n");
                for (Car c : list) sb.append("<code>").append(prettyPlate(c.getPlateNumber()).replace(" ", "")).append(" ").append(km).append("</code>\n");
                return sb.toString().trim();
            }
        }
        try {
            boolean changed = apply(car, km, "Telegram bot", true);
            return (changed ? "✅ " : "ℹ️ ") + "<b>" + html(prettyPlate(car.getPlateNumber())) + "</b> probegi: <b>" + km(km) + "</b>"
                    + (changed ? "" : " (o'zgarmadi)") + "\n\n" + reminders.mileageSummary(car);
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
        // Mashina raqami bilan yozilgan bo'lsa ("01A123BC 123456") raqam qismi oxirida — m.find() oxirgisini oladi
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
        return c.getCurrentMileage() == null ? "kiritilmagan" : km(c.getCurrentMileage());
    }

    public static String km(int v) {
        return String.format("%,d", v).replace(',', ' ') + " km";
    }
}
