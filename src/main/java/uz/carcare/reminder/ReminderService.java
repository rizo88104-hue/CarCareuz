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
import uz.carcare.i18n.I18n;
import uz.carcare.i18n.LangContext;
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
 *  - muddat kelgan kuni — "bugun almashtirish kuni";
 *  - probeg bo'yicha: keyingi probegga REMINDER_KM qolganda va yetganda.
 * Har kuni soat 09:00 da (Toshkent vaqti) tekshiriladi, dastur ishga tushganda ham bir marta.
 * Xabarlar mijozning tilida (Car.lang).
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

    /** @return xabar yuborilgan bo'lsa true. Yetkazilmasa keyingi kuni yana urinadi. */
    private boolean process(ServiceRecord r, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, r.getNextDate());
        Car car = r.getCar();
        return LangContext.with(car.lang(), () -> {
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
        });
    }

    /** @return xabar yuborilgan bo'lsa true. */
    private boolean processKm(ServiceRecord r) {
        Integer left = r.kmLeft();
        if (left == null) return false;
        Car car = r.getCar();
        return LangContext.with(car.lang(), () -> {
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
        });
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
                boolean ok = LangContext.with(car.lang(), () -> {
                    String text = I18n.t("bot.km.ask", html(prettyPlate(car.getPlateNumber())),
                            car.getCurrentMileage() == null ? I18n.t("bot.km.none") : MileageService.km(car.getCurrentMileage()))
                            + "\n\n" + mileageSummary(car);
                    return car.getTelegramChatId() != null && notifications.telegram(car, text);
                });
                if (ok) {
                    car.setMileageAskedOn(today);
                    asked++;
                }
            } catch (Exception e) {
                log.warn("Probeg so'rovini yuborib bo'lmadi (car={}): {}", car.getId(), e.getMessage());
            }
        }
        if (asked > 0) log.info("{} ta mijozdan probeg so'raldi", asked);
    }

    /** Mashinaning km bo'yicha servislari qisqacha (bot javoblarida, joriy tilda). */
    public String mileageSummary(Car car) {
        List<ServiceRecord> list = records.findAllByCarAndActiveTrueAndNextMileageIsNotNull(car);
        if (list.isEmpty()) return I18n.t("bot.km.noKmServices");
        StringBuilder sb = new StringBuilder(I18n.t("bot.km.servicesTitle")).append("\n");
        for (ServiceRecord r : list) {
            Integer left = r.kmLeft();
            String state = left == null ? "" : left <= 0 ? " — 🔴 " + I18n.t("bot.km.reached", MileageService.km(-left))
                    : left <= app.reminderKmBefore() ? " — 🟡 " + I18n.t("bot.km.left", MileageService.km(left))
                    : " — 🟢 " + I18n.t("bot.km.left", MileageService.km(left));
            sb.append("• ").append(html(r.displayName())).append(": ").append(I18n.t("bot.km.at", MileageService.km(r.getNextMileage()))).append(state).append("\n");
        }
        return sb.toString().trim();
    }

    // ---------- Xabar matnlari (joriy til — LangContext) ----------

    private String weekTelegram(ServiceRecord r, long days) {
        Car car = r.getCar();
        return I18n.t("bot.rem.week", html(prettyPlate(car.getPlateNumber())), html(r.displayName()), date(r.getNextDate()), days,
                mileageLine(r), app.carUrl(car.getToken()));
    }

    private String dueTelegram(ServiceRecord r, long days) {
        Car car = r.getCar();
        String when = days == 0 ? I18n.t("bot.rem.dueToday") : I18n.t("bot.rem.overdue", -days);
        return I18n.t("bot.rem.due", when, html(prettyPlate(car.getPlateNumber())), html(r.displayName()), date(r.getNextDate()),
                mileageLine(r), app.carUrl(car.getToken()));
    }

    private String kmSoonTelegram(ServiceRecord r, int left) {
        Car car = r.getCar();
        return I18n.t("bot.rem.kmSoon", html(prettyPlate(car.getPlateNumber())), html(r.displayName()),
                MileageService.km(car.getCurrentMileage()), MileageService.km(r.getNextMileage()), MileageService.km(left),
                centerLine(r), app.carUrl(car.getToken()));
    }

    private String kmDueTelegram(ServiceRecord r, int left) {
        Car car = r.getCar();
        String when = left == 0 ? I18n.t("bot.rem.kmReached") : I18n.t("bot.rem.kmOver", MileageService.km(-left));
        return I18n.t("bot.rem.kmDue", when, html(prettyPlate(car.getPlateNumber())), html(r.displayName()),
                MileageService.km(car.getCurrentMileage()), MileageService.km(r.getNextMileage()),
                centerLine(r), app.carUrl(car.getToken()));
    }

    private String mileageLine(ServiceRecord r) {
        String s = r.getNextMileage() == null ? "" : I18n.t("bot.rem.byKm", MileageService.km(r.getNextMileage())) + "\n";
        return s + centerLine(r);
    }

    private String centerLine(ServiceRecord r) {
        ServiceCenter c = r.getCenter();
        return c == null ? "" : I18n.t("bot.rem.center", html(c.getName()))
                + (c.getPhone() == null ? "" : ", 📞 " + prettyPhone(c.getPhone())) + "\n";
    }

    private String weekSms(ServiceRecord r, long days) {
        return I18n.t("sms.rem.week", prettyPlate(r.getCar().getPlateNumber()), r.displayName(), days, date(r.getNextDate()));
    }

    private String dueSms(ServiceRecord r, long days) {
        String when = days == 0 ? I18n.t("sms.rem.dueToday") : I18n.t("sms.rem.overdue", -days);
        return I18n.t("sms.rem.due", prettyPlate(r.getCar().getPlateNumber()), r.displayName(), when, date(r.getNextDate()));
    }

    private String kmSoonSms(ServiceRecord r, int left) {
        return I18n.t("sms.rem.kmSoon", prettyPlate(r.getCar().getPlateNumber()), r.displayName(), left, r.getNextMileage());
    }

    private String kmDueSms(ServiceRecord r, int left) {
        String when = left == 0 ? I18n.t("sms.rem.kmReached") : I18n.t("sms.rem.kmOver", -left);
        return I18n.t("sms.rem.kmDue", prettyPlate(r.getCar().getPlateNumber()), r.displayName(), when, r.getNextMileage());
    }
}
