package uz.carcare.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.admin.AdminDtos.*;
import uz.carcare.config.AppProperties;
import uz.carcare.i18n.I18n;
import uz.carcare.model.ServiceCenter;
import uz.carcare.model.SubscriptionPayment;
import uz.carcare.repo.ServiceCenterRepository;
import uz.carcare.repo.SubscriptionPaymentRepository;
import uz.carcare.web.ApiException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static uz.carcare.util.Formats.money;

/**
 * Avtoservislardan oylik to'lov (obuna, abonement).
 *
 *  - Narx: SUBSCRIPTION_PRICE (standart 200 000 so'm/oy).
 *  - Yangi servisga sinov muddati beriladi (SUBSCRIPTION_TRIAL_DAYS, standart 14 kun); eski servislarga
 *    (muddati belgilanmagan) ilova ishga tushganda ham shu muddat beriladi.
 *  - Super admin to'lovni qabul qiladi: muddat max(bugun, oldingi muddat) dan N oyga uzayadi, to'lov jurnalga yoziladi.
 *  - Muddat tugagach imtiyoz kunlari (SUBSCRIPTION_GRACE_DAYS, standart 3) ichida hali ishlaydi, panelda ogohlantirish;
 *    keyin servisning barcha xodimlari kira olmaydi (sessiyalar ham rad etiladi) — to'lov qabul qilingach darhol ochiladi.
 */
@Service
@Order(5)
public class SubscriptionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    /** Tugashiga shuncha kun qolganda panelda ogohlantirish. */
    private static final int WARN_DAYS = 7;

    private final ServiceCenterRepository centers;
    private final SubscriptionPaymentRepository payments;
    private final AuditService audit;
    private final AppProperties app;

    public SubscriptionService(ServiceCenterRepository centers, SubscriptionPaymentRepository payments,
                               AuditService audit, AppProperties app) {
        this.centers = centers;
        this.payments = payments;
        this.audit = audit;
        this.app = app;
    }

    /** Muddati belgilanmagan servislarga sinov muddati beriladi (eski bazadan o'tishda). */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate until = today().plusDays(app.subscriptionTrialDays());
        int n = 0;
        for (ServiceCenter c : centers.findAllByOrderByNameAsc()) {
            if (c.getSubscriptionUntil() == null) {
                c.setSubscriptionUntil(until);
                n++;
            }
        }
        if (n > 0) log.info("{} ta servisga obuna sinov muddati berildi: {} gacha", n, until);
    }

    /** Yangi servis uchun boshlang'ich muddat. */
    public LocalDate trialUntil() {
        return today().plusDays(app.subscriptionTrialDays());
    }

    /** Xodim kira oladimi: muddat + imtiyoz kunlari o'tmagan bo'lsa. */
    public boolean isBlocked(ServiceCenter c) {
        LocalDate until = c.getSubscriptionUntil();
        return until != null && today().isAfter(until.plusDays(app.subscriptionGraceDays()));
    }

    /** Panel uchun holat. */
    public SubscriptionInfo info(ServiceCenter c) {
        LocalDate today = today();
        LocalDate until = c.getSubscriptionUntil();
        if (until == null) return new SubscriptionInfo(null, null, "NONE", app.subscriptionPrice(), app.subscriptionGraceDays());
        long left = ChronoUnit.DAYS.between(today, until);
        String state = left < -app.subscriptionGraceDays() ? "EXPIRED" : left < 0 ? "GRACE" : left <= WARN_DAYS ? "EXPIRING" : "ACTIVE";
        return new SubscriptionInfo(until, left, state, app.subscriptionPrice(), app.subscriptionGraceDays());
    }

    @Transactional(readOnly = true)
    public SubscriptionDetail detail(Long centerId) {
        ServiceCenter c = center(centerId);
        return new SubscriptionDetail(info(c), payments.findAllByCenterOrderByCreatedAtDesc(c).stream().map(SubscriptionService::row).toList());
    }

    /** To'lov qabul qilish: muddat N oyga uzayadi. */
    @Transactional
    public SubscriptionDetail pay(AdminPrincipal p, Long centerId, SubscriptionPayRequest req) {
        ServiceCenter c = center(centerId);
        int months = req.months() == null ? 1 : req.months();
        if (months < 1 || months > 36) throw ApiException.badRequest(I18n.t("err.sub.months"));
        long amount = req.amount() == null ? months * app.subscriptionPrice() : req.amount();
        if (amount < 0) throw ApiException.badRequest(I18n.t("err.sub.amount"));
        LocalDate today = today();
        LocalDate from = c.getSubscriptionUntil() == null || c.getSubscriptionUntil().isBefore(today) ? today : c.getSubscriptionUntil();
        LocalDate to = from.plusMonths(months);
        c.setSubscriptionUntil(to);
        payments.save(new SubscriptionPayment(c, amount, months, from, to, req.note() == null ? null : req.note().trim(), p.fullName()));
        audit.staff(p, c, "«" + c.getName() + "» obuna to'lovi: " + money(amount) + ", " + months + " oy, " + to + " gacha"
                + (req.note() == null || req.note().isBlank() ? "" : " (" + req.note().trim() + ")"));
        return detail(centerId);
    }

    /** Muddatni qo'lda o'zgartirish (masalan, sinovni uzaytirish yoki to'lovsiz yopish). */
    @Transactional
    public SubscriptionDetail setUntil(AdminPrincipal p, Long centerId, SubscriptionSetRequest req) {
        ServiceCenter c = center(centerId);
        if (req.until() == null) throw ApiException.badRequest(I18n.t("err.sub.date"));
        c.setSubscriptionUntil(req.until());
        audit.staff(p, c, "«" + c.getName() + "» obuna muddati qo'lda o'zgartirildi: " + req.until()
                + (req.note() == null || req.note().isBlank() ? "" : " (" + req.note().trim() + ")"));
        return detail(centerId);
    }

    private static SubscriptionPaymentRow row(SubscriptionPayment x) {
        return new SubscriptionPaymentRow(x.getId(), x.getCreatedAt(), x.getAmount(), x.getMonths(), x.getPeriodFrom(), x.getPeriodTo(),
                x.getNote(), x.getRecordedBy());
    }

    private ServiceCenter center(Long id) {
        return centers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("err.center.notFound")));
    }

    private LocalDate today() {
        return LocalDate.now(app.zoneId());
    }
}
