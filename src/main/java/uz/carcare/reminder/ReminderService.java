package uz.carcare.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.admin.AuditService;
import uz.carcare.config.AppProperties;
import uz.carcare.model.Car;
import uz.carcare.model.ServiceCenter;
import uz.carcare.model.ServiceRecord;
import uz.carcare.repo.CarRepository;
import uz.carcare.repo.ServiceRecordRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static uz.carcare.util.Formats.*;

/**
 * Eslatmalar:
 *  - muddatga 7 kun qolganda (app.reminder-days-before) — "1 haftadan keyin almashtiring";
 *  - muddat kelgan kuni — "bugun almashtirish kuni".
 * Har kuni soat 09:00 da (Toshkent vaqti) tekshiriladi, dastur ishga tushganda ham bir marta.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ServiceRecordRepository records;
    private final CarRepository cars;
    private final NotificationService notifications;
    private final AppProperties app;
    private final AuditService audit;

    public ReminderService(ServiceRecordRepository records, CarRepository cars, NotificationService notifications,
                           AppProperties app, AuditService audit) {
        this.cars = cars;
        this.audit = audit;
        this.records = records;
        this.notifications = notifications;
        this.app = app;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runDaily();
    }

    @Scheduled(cron = "${app.reminder-cron:0 0 9 * * *}", zone = "${app.zone:Asia/Tashkent}")
    @Transactional
    public void runDaily() {
        LocalDate today = LocalDate.now(app.zoneId());
        List<ServiceRecord> pending = records.findPendingReminders(today, today.plusDays(app.reminderDaysBefore()));
        int sent = 0;
        for (ServiceRecord r : pending) {
            try {
                if (process(r, today)) sent++;
            } catch (Exception e) {
                log.error("Eslatma yuborishda xato (record={})", r.getId(), e);
            }
        }
        records.saveAll(pending);
        log.info("Eslatmalar tekshirildi: {} ta navbatda, {} ta yuborildi", pending.size(), sent);
        if (!pending.isEmpty()) {
            int failed = pending.size() - sent;
            audit.system("Tizim", "Bot", null, sent + " ta eslatma yuborildi"
                    + (failed > 0 ? ", " + failed + " tasi yetkazilmadi (Telegram ulanmagan yoki bot bloklangan)" : ""));
        }

        // Probeg bo'yicha: oldin yetkazilmagan eslatmalarni qayta urinish
        List<ServiceRecord> kmPending = records.findPendingMileageReminders(app.reminderKmBefore());
        int kmSent = 0;
        for (ServiceRecord r : kmPending) {
            try {
                if (processKm(r)) kmSent++;
            } catch (Exception e) {
                log.error("Probeg eslatmasida xato (record={})", r.getId(), e);
            }
        }
        if (!kmPending.isEmpty()) log.info("Probeg eslatmalari: {} ta navbatda, {} ta yuborildi", kmPending.size(), kmSent);

        askMileage(today);
    }

    /** Yangi yozuv qo'shilganda darhol tekshirish (masalan muddatga 3 kun qolgan bo'lsa). */
    public void checkNow(ServiceRecord r) {
        process(r, LocalDate.now(app.zoneId()));
        if (r.getNextMileage() != null && r.getCar().getCurrentMileage() != null) processKm(r);
    }

    /** Probeg yangilanganda: shu mashinaning km bo'yicha eslatmalarini darhol tekshirish. */
    public void checkMileage(Car car) {
        if (car.getCurrentMileage() == null) return;
        for (ServiceRecord r : records.findAllByCarAndActiveTrueAndNextMileageIsNotNull(car)) {
            try {
                processKm(r);
            } catch (Exception e) {
                log.error("Probeg eslatmasida xato (record={})", r.getId(), e);
            }
        }
    }

    /** @return xabar yuborilgan bo'lsa true. */
    private boolean processKm(ServiceRecord r) {
        Integer left = r.kmLeft();
        if (left == null) return false;
        Car car = r.getCar();
        if (left <= 0 && !r.isKmDueSent()) {
            if (notifications.notify(car, kmDueTelegram(r, left), kmDueSms(r, left))) {
                r.setKmDueSent(true);
                r.setKmSoonSent(true);
                return true;
            }
        } else if (left > 0 && left <= app.reminderKmBefore() && !r.isKmSoonSent()) {
            if (notifications.notify(car, kmSoonTelegram(r, left), kmSoonSms(r, left))) {
                r.setKmSoonSent(true);
                return true;
            }
        }
        return false;
    }

    /**
     * Probegi 30 kundan beri yangilanmagan va km bo'yicha servisi bor mijozlardan botda probegni so'rash
     * (30 kunda bir marta). Mijoz javoban raqam yozsa — probeg yangilanadi va km eslatmalari tekshiriladi.
     */
    private void askMileage(LocalDate today) {
        List<Car> list = cars.findNeedingMileagePrompt(
                today.minusDays(30).atStartOfDay(app.zoneId()).toInstant(), today.minusDays(30));
        int asked = 0;
        for (Car car : list) {
            try {
                String text = "🛣 <b>Probegingiz qancha?</b>\n\n"
                        + "🚗 " + html(prettyPlate(car.getPlateNumber())) + " — oxirgi ma'lum probeg: "
                        + (car.getCurrentMileage() == null ? "kiritilmagan" : MileageService.km(car.getCurrentMileage())) + "\n\n"
                        + "Odometrdagi raqamni shu yerga yozing (masalan <code>123456</code>) — "
                        + "probeg bo'yicha servis vaqti yaqinlashsa, eslataman.\n\n" + mileageSummary(car);
                if (telegramOnly(car, text)) {
                    car.setMileageAskedOn(today);
                    asked++;
                }
            } catch (Exception e) {
                log.warn("Probeg so'rovini yuborib bo'lmadi (car={}): {}", car.getId(), e.getMessage());
            }
        }
        if (asked > 0) log.info("{} ta mijozdan probeg so'raldi", asked);
    }

    private boolean telegramOnly(Car car, String html) {
        return car.getTelegramChatId() != null && notifications.telegram(car, html);
    }

    /** Mashinaning km bo'yicha servislari qisqacha (bot javoblarida). */
    public String mileageSummary(Car car) {
        List<ServiceRecord> list = records.findAllByCarAndActiveTrueAndNextMileageIsNotNull(car);
        if (list.isEmpty()) return "Probeg bo'yicha belgilangan servis yo'q.";
        StringBuilder sb = new StringBuilder("Probeg bo'yicha servislar:\n");
        for (ServiceRecord r : list) {
            Integer left = r.kmLeft();
            String state = left == null ? "" : left <= 0 ? " — 🔴 probeg yetdi (" + MileageService.km(-left) + " o'tdi)"
                    : left <= app.reminderKmBefore() ? " — 🟡 " + MileageService.km(left) + " qoldi" : " — 🟢 " + MileageService.km(left) + " qoldi";
            sb.append("• ").append(html(r.displayName())).append(": ").append(MileageService.km(r.getNextMileage())).append(" da").append(state).append("\n");
        }
        return sb.toString().trim();
    }

    /** @return xabar yuborilgan bo'lsa true. Yetkazilmasa keyingi kuni yana urinadi. */
    private boolean process(ServiceRecord r, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, r.getNextDate());
        Car car = r.getCar();

        if (days <= 0 && !r.isDueReminderSent()) {
            if (notifications.notify(car, dueTelegram(r, days), dueSms(r, days))) {
                r.setDueReminderSent(true);
                r.setWeekReminderSent(true);
                return true;
            }
        } else if (days > 0 && days <= app.reminderDaysBefore() && !r.isWeekReminderSent()) {
            if (notifications.notify(car, weekTelegram(r, days), weekSms(r, days))) {
                r.setWeekReminderSent(true);
                return true;
            }
        }
        return false;
    }

    // ---------- Xabar matnlari ----------

    private String weekTelegram(ServiceRecord r, long days) {
        Car car = r.getCar();
        return "🔔 <b>Eslatma</b>\n\n"
                + "🚗 Mashina: <b>" + html(prettyPlate(car.getPlateNumber())) + "</b>\n"
                + "🔧 Servis: <b>" + html(r.displayName()) + "</b>\n"
                + "📅 Almashtirish sanasi: <b>" + date(r.getNextDate()) + "</b>\n"
                + "⏳ <b>" + days + " kun</b> qoldi\n"
                + mileageLine(r)
                + "\nOldindan servisga yozilib qo'ying 👍\n"
                + "Sahifa: " + app.carUrl(car.getToken());
    }

    private String dueTelegram(ServiceRecord r, long days) {
        Car car = r.getCar();
        String when = days == 0
                ? "⚠️ <b>Bugun almashtirish kuni!</b>"
                : "❗️ Muddati <b>" + (-days) + " kun</b> o'tib ketdi!";
        return when + "\n\n"
                + "🚗 Mashina: <b>" + html(prettyPlate(car.getPlateNumber())) + "</b>\n"
                + "🔧 Servis: <b>" + html(r.displayName()) + "</b>\n"
                + "📅 Belgilangan sana: <b>" + date(r.getNextDate()) + "</b>\n"
                + mileageLine(r)
                + "\nAlmashtirgandan keyin sahifada yangi sanani belgilang:\n"
                + app.carUrl(car.getToken());
    }

    private String kmSoonTelegram(ServiceRecord r, int left) {
        Car car = r.getCar();
        return "🛣 <b>Probeg bo'yicha eslatma</b>\n\n"
                + "🚗 Mashina: <b>" + html(prettyPlate(car.getPlateNumber())) + "</b>\n"
                + "🔧 Servis: <b>" + html(r.displayName()) + "</b>\n"
                + "📍 Hozirgi probeg: <b>" + MileageService.km(car.getCurrentMileage()) + "</b>\n"
                + "🎯 Almashtirish probegi: <b>" + MileageService.km(r.getNextMileage()) + "</b>\n"
                + "⏳ <b>" + MileageService.km(left) + "</b> qoldi\n"
                + centerLine(r)
                + "\nOldindan servisga yozilib qo'ying 👍\n"
                + "Sahifa: " + app.carUrl(car.getToken());
    }

    private String kmDueTelegram(ServiceRecord r, int left) {
        Car car = r.getCar();
        String when = left == 0 ? "⚠️ <b>Almashtirish probegiga yetdingiz!</b>"
                : "❗️ Almashtirish probegidan <b>" + MileageService.km(-left) + "</b> o'tib ketdi!";
        return when + "\n\n"
                + "🚗 Mashina: <b>" + html(prettyPlate(car.getPlateNumber())) + "</b>\n"
                + "🔧 Servis: <b>" + html(r.displayName()) + "</b>\n"
                + "📍 Hozirgi probeg: <b>" + MileageService.km(car.getCurrentMileage()) + "</b>\n"
                + "🎯 Belgilangan probeg: <b>" + MileageService.km(r.getNextMileage()) + "</b>\n"
                + centerLine(r)
                + "\nAlmashtirgandan keyin sahifada yangisini belgilang:\n" + app.carUrl(car.getToken());
    }

    private String kmSoonSms(ServiceRecord r, int left) {
        return "Eslatma: " + prettyPlate(r.getCar().getPlateNumber()) + " mashinangizda " + r.displayName()
                + " almashtirishga " + left + " km qoldi (" + r.getNextMileage() + " km da).";
    }

    private String kmDueSms(ServiceRecord r, int left) {
        return "Eslatma: " + prettyPlate(r.getCar().getPlateNumber()) + " mashinangizda " + r.displayName()
                + " almashtirish probegi " + (left == 0 ? "yetdi" : (-left) + " km o'tdi") + " (" + r.getNextMileage() + " km).";
    }

    private String centerLine(ServiceRecord r) {
        ServiceCenter c = r.getCenter();
        return c == null ? "" : "📍 Servis: <b>" + html(c.getName()) + "</b>"
                + (c.getPhone() == null ? "" : ", 📞 " + prettyPhone(c.getPhone())) + "\n";
    }

    private String mileageLine(ServiceRecord r) {
        String s = r.getNextMileage() == null ? "" : "🛣 Probeg bo'yicha: <b>" + r.getNextMileage() + " km</b>\n";
        ServiceCenter c = r.getCenter();
        if (c != null) {
            s += "📍 Servis: <b>" + html(c.getName()) + "</b>"
                    + (c.getPhone() == null ? "" : ", 📞 " + prettyPhone(c.getPhone())) + "\n";
        }
        return s;
    }

    private String weekSms(ServiceRecord r, long days) {
        return "Eslatma: " + prettyPlate(r.getCar().getPlateNumber()) + " mashinangizda "
                + r.displayName() + " almashtirishga " + days + " kun qoldi (" + date(r.getNextDate()) + ").";
    }

    private String dueSms(ServiceRecord r, long days) {
        String when = days == 0 ? "bugun almashtirish kuni" : "almashtirish muddati " + (-days) + " kun o'tdi";
        return "Eslatma: " + prettyPlate(r.getCar().getPlateNumber()) + " mashinangizda "
                + r.displayName() + " " + when + " (" + date(r.getNextDate()) + ").";
    }
}
