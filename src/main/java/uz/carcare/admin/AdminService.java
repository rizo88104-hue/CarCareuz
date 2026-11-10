package uz.carcare.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.admin.AdminDtos.*;
import uz.carcare.config.AppProperties;
import uz.carcare.config.TelegramProperties;
import uz.carcare.i18n.I18n;
import uz.carcare.i18n.LangContext;
import uz.carcare.loyalty.LoyaltyService;
import uz.carcare.sms.SmsService;
import uz.carcare.util.QrCodes;
import uz.carcare.model.*;
import uz.carcare.model.StaffUser.Role;
import uz.carcare.reminder.MileageService;
import uz.carcare.reminder.ReminderService;
import uz.carcare.repo.*;
import uz.carcare.telegram.TelegramClient;
import uz.carcare.web.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static uz.carcare.util.Formats.*;

@Service
public class AdminService {

    /** Shuncha kun hech narsa kiritmagan servis "faol emas" deb belgilanadi. */
    private static final int INACTIVE_AFTER_DAYS = 14;
    /** Jurnaldan bir martada ko'rsatiladigan yozuvlar soni. */
    private static final int LOG_LIMIT = 300;

    private final CarRepository cars;
    private final ServiceRecordRepository records;
    private final ServiceCenterRepository centers;
    private final StaffUserRepository staff;
    private final AdminSessionRepository sessions;
    private final ActivityLogRepository logs;
    private final PaymentRepository payments;
    private final CustomerNoteRepository customerNotes;
    private final AdminAuthService auth;
    private final AuditService audit;
    private final ReminderService reminders;
    private final MileageService mileage;
    private final TelegramClient telegram;
    private final TelegramProperties telegramProps;
    private final SmsService sms;
    private final AppProperties app;
    private final LoyaltyService loyalty;
    private final SubscriptionService subscription;

    public AdminService(CarRepository cars, ServiceRecordRepository records, ServiceCenterRepository centers,
                        StaffUserRepository staff, AdminSessionRepository sessions, ActivityLogRepository logs,
                        PaymentRepository payments, CustomerNoteRepository customerNotes,
                        AdminAuthService auth, AuditService audit, ReminderService reminders, MileageService mileage,
                        TelegramClient telegram, TelegramProperties telegramProps, SmsService sms,
                        AppProperties app, LoyaltyService loyalty, SubscriptionService subscription) {
        this.subscription = subscription;
        this.telegramProps = telegramProps;
        this.sms = sms;
        this.mileage = mileage;
        this.loyalty = loyalty;
        this.payments = payments;
        this.customerNotes = customerNotes;
        this.cars = cars;
        this.records = records;
        this.centers = centers;
        this.staff = staff;
        this.sessions = sessions;
        this.logs = logs;
        this.auth = auth;
        this.audit = audit;
        this.reminders = reminders;
        this.telegram = telegram;
        this.app = app;
    }

    // =====================================================================
    //  Servis xodimlari: ish kiritish
    // =====================================================================

    @Transactional(readOnly = true)
    public CarLookupDto lookup(AdminPrincipal p, String plateRaw) {
        String plate = normalizePlate(plateRaw);
        boolean showPhone = p.canContactCustomers();
        ServiceCenter center = center(p);
        return cars.findByPlateNumber(plate).map(car -> new CarLookupDto(true, plate, prettyPlate(plate),
                        car.getFirstName(), car.getLastName(),
                        showPhone ? car.getPhone() : null, showPhone ? prettyPhone(car.getPhone()) : null,
                        car.getTelegramChatId() != null,
                        records.findAllByCarAndActiveTrueOrderByNextDateAsc(car).stream()
                                .map(r -> r.displayName() + " — " + date(r.getNextDate())
                                        + (r.getNextMileage() == null ? "" : " / " + MileageService.km(r.getNextMileage()))).toList(),
                        customerNotes.findByCenterAndCar(center, car)
                                .filter(CustomerNote::isBlacklisted).map(CustomerNote::getBlacklistReason).orElse(null),
                        showPhone ? loyalty.lookup(center, car) : null,
                        car.getCurrentMileage()))
                .orElseGet(() -> new CarLookupDto(false, plate, prettyPlate(plate), null, null, null, null, false, List.of(), null,
                        showPhone ? loyalty.lookup(center, null) : null, null));
    }

    @Transactional
    public SaveResult addRecord(AdminPrincipal p, AdminRecordRequest req) {
        ServiceCenter center = center(p);
        LocalDate today = today();

        if (req.doneDate().isAfter(today)) {
            throw ApiException.badRequest(I18n.t("err.rec.doneFuture"));
        }
        // Bir tashrifdagi ishlar. Eski format (bitta type) ham qabul qilinadi.
        List<RecordItem> items = req.items() != null && !req.items().isEmpty() ? req.items()
                : List.of(new RecordItem(req.type(), req.customName(), req.nextDate(), req.nextMileage(), req.price()));
        if (items.size() > 10) throw ApiException.badRequest(I18n.t("err.visit.max10"));
        Set<ServiceType> seen = new HashSet<>();
        for (RecordItem it : items) {
            if (it == null || it.type() == null) throw ApiException.badRequest(I18n.t("err.visit.pickType"));
            String label = it.type() == ServiceType.OTHER && it.customName() != null && !it.customName().isBlank()
                    ? it.customName().trim() : it.type().getLabel();
            if (it.type() == ServiceType.OTHER && (it.customName() == null || it.customName().isBlank())) {
                throw ApiException.badRequest(I18n.t("err.rec.otherName"));
            }
            if (it.type() != ServiceType.OTHER && !seen.add(it.type())) {
                throw ApiException.badRequest(I18n.t("err.visit.duplicate", label));
            }
            if (it.nextDate() == null) throw ApiException.badRequest(I18n.t("err.visit.nextDateMissing", label));
            if (it.nextDate().isBefore(req.doneDate())) {
                throw ApiException.badRequest(I18n.t("err.visit.nextBeforeDone", label));
            }
        }

        // Ishni kim qilgani: usta o'zi, direktor/administrator esa ustani tanlaydi
        String masterName = p.fullName();
        if (!p.isMaster() && req.masterId() != null) {
            StaffUser m = staff.findByIdAndCenter(req.masterId(), center)
                    .filter(s -> !s.isBlocked())
                    .orElseThrow(() -> ApiException.badRequest(I18n.t("err.visit.masterNotFound")));
            masterName = m.displayName();
        }

        String plate = normalizePlate(req.plateNumber());
        Car car = cars.findByPlateNumber(plate).orElse(null);

        // Narx va to'lov: faqat direktor va administrator kiritadi.
        // Har bir ish narxi — chegirmagacha; sadoqat chegirmasi har ishga alohida qo'llanadi.
        // To'lov va ball butun tashrif (jami) uchun kiritiladi va ishlar bo'yicha taqsimlanadi.
        boolean staffMoney = !p.isMaster();
        int discountPct = staffMoney && !Boolean.FALSE.equals(req.applyDiscount()) ? loyalty.discountPct(center, car) : 0;
        long listTotal = 0, total = 0, discountTotal = 0;
        long[] finals = new long[items.size()], discounts = new long[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Long pr = items.get(i).price();
            if (!staffMoney || pr == null || pr <= 0) continue;
            discounts[i] = LoyaltyService.percent(pr, discountPct);
            finals[i] = pr - discounts[i];
            listTotal += pr;
            discountTotal += discounts[i];
            total += finals[i];
        }
        boolean money = total > 0;
        long bonusUse = money && req.bonus() != null ? req.bonus() : 0;
        if (bonusUse > total) throw ApiException.badRequest(I18n.t("err.pay.bonusOverTotal"));
        long paidNow = money ? (req.paid() == null ? total - bonusUse : req.paid()) : 0;
        if (money) {
            if (paidNow + bonusUse > total) {
                throw ApiException.badRequest(bonusUse > 0 ? I18n.t("err.pay.bonusAndPaidOver", money(total))
                        : I18n.t("err.pay.paidOver", money(total)));
            }
            if (paidNow + bonusUse < total) {
                if (req.payDueDate() == null) throw ApiException.badRequest(I18n.t("err.pay.dueMissing"));
                if (req.payDueDate().isBefore(today)) throw ApiException.badRequest(I18n.t("err.pay.duePast"));
            }
        }

        boolean created = false;
        if (car == null) {
            if (req.firstName() == null || req.firstName().isBlank()) {
                throw ApiException.badRequest(I18n.t("err.visit.newCustomerName"));
            }
            car = new Car();
            car.setToken(newToken());
            car.setFirstName(trim(req.firstName()));
            car.setLastName(req.lastName() == null ? "" : trim(req.lastName()));
            car.setPhone(normalizePhone(req.phone()));
            car.setPlateNumber(plate);
            cars.save(car);
            created = true;
            audit.staff(p, center, "Yangi mijoz qo'shdi: " + car.getFullName().trim() + ", " + prettyPlate(plate));
        }

        // Yozuvlar: har bir ish alohida
        List<ServiceRecord> saved = new ArrayList<>();
        List<ServiceRecord> priced = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            RecordItem it = items.get(i);
            if (it.type() != ServiceType.OTHER) {
                for (ServiceRecord old : records.findAllByCarAndTypeAndActiveTrue(car, it.type())) {
                    old.markReplaced(center);
                }
            }
            ServiceRecord r = new ServiceRecord();
            r.setCar(car);
            r.setCenter(center);
            r.setType(it.type());
            r.setCustomName(it.type() == ServiceType.OTHER ? trim(it.customName()) : null);
            r.setDoneDate(req.doneDate());
            r.setNextDate(it.nextDate());
            r.setMileage(req.mileage());
            r.setNextMileage(it.nextMileage());
            r.setNote(trim(req.note()));
            r.setMasterName(masterName);
            if (finals[i] > 0) {
                r.setPrice(finals[i]);
                r.setDiscount(discounts[i]);
                priced.add(r);
            }
            records.save(r);
            saved.add(r);
        }

        // Sadoqat: tashrif bir marta (karta bir marta ochiladi), keyin ball, keyin pul to'lovi va keshbek
        loyalty.onVisit(center, car, p.fullName());
        if (bonusUse > 0) {
            LoyaltyProgram prog = loyalty.activeProgram(center);
            LoyaltyAccount acc = loyalty.account(center, car).orElse(null);
            long capTotal = 0;
            for (ServiceRecord r : priced) capTotal += loyalty.redeemCap(prog, acc, r);
            if (prog == null || acc == null || bonusUse > Math.min(capTotal, acc.getBalance())) {
                long allowed = acc == null ? 0 : Math.min(capTotal, acc.getBalance());
                throw ApiException.badRequest(I18n.t("err.pay.bonusCap", money(allowed))
                        + (prog == null ? "" : " " + I18n.t("err.pay.bonusCapPct", prog.getMaxRedeemPct())));
            }
            long left = bonusUse;
            for (ServiceRecord r : priced) {
                if (left == 0) break;
                long part = Math.min(left, loyalty.redeemCap(prog, acc, r));
                if (part <= 0) continue;
                loyalty.redeem(center, r, part, p.fullName(), "Ish kiritilganda");
                left -= part;
            }
        }
        Payment.Method method = req.paymentMethod() == null || req.paymentMethod() == Payment.Method.BONUS
                ? Payment.Method.CASH : req.paymentMethod();
        long earnedPoints = 0;
        LoyaltyService.Earn lastEarn = null;
        boolean levelUp = false;
        long left = paidNow;
        for (ServiceRecord r : priced) {
            if (left == 0) break;
            long part = Math.min(left, r.getRemaining());
            if (part <= 0) continue;
            payments.save(new Payment(r, center, part, method, p.fullName(), "Ish kiritilganda"));
            r.addPaid(part);
            left -= part;
            LoyaltyService.Earn e = loyalty.onMoneyPayment(center, r, part, p.fullName());
            if (e != null) {
                earnedPoints += e.points();
                levelUp |= e.levelUp();
                lastEarn = e;
            }
        }
        LoyaltyService.Earn earn = lastEarn == null ? null
                : new LoyaltyService.Earn(earnedPoints, lastEarn.balance(), lastEarn.level(), levelUp, lastEarn.cashbackPct());
        long debt = 0;
        for (ServiceRecord r : priced) {
            if (r.getRemaining() > 0) {
                r.setCredit(true);
                r.setPayDueDate(req.payDueDate());
                debt += r.getRemaining();
            }
        }
        // Usta kiritgan probeg mashinaning hozirgi probegi bo'ladi (kamroq bo'lsa e'tiborsiz) — km eslatmalari uchun
        if (req.mileage() != null) mileage.apply(car, req.mileage(), "avtoservis «" + center.getName() + "»", false);
        for (ServiceRecord r : saved) reminders.checkNow(r);

        String names = saved.stream().map(ServiceRecord::displayName).collect(java.util.stream.Collectors.joining(", "));
        audit.staff(p, center, prettyPlate(plate) + " — " + names + " kiritdi (usta: " + masterName + ")"
                + (money ? ", " + (saved.size() > 1 ? "jami narx " : "narx ") + money(total) : "")
                + (discountTotal > 0 ? " (chegirma " + discountPct + "%: −" + money(discountTotal) + ")" : "")
                + (bonusUse > 0 ? ", ball bilan " + money(bonusUse) : "")
                + (debt > 0 ? ", qarz " + money(debt) : ""));

        // Sadoqat ishtirok etgan bo'lsa — mijozga bitta kvitansiya (barcha ishlar bilan)
        boolean loyaltyUsed = discountTotal > 0 || bonusUse > 0 || (earn != null && earn.points() > 0);
        if (money && loyaltyUsed && car.getTelegramChatId() != null) {
            final Car cc = car;
            final Payment.Method m = method;
            final long fDebt = debt, fPaid = paidNow, fBonus = bonusUse, fTotal = total, fList = listTotal, fDisc = discountTotal;
            final int fPct = discountPct;
            final LoyaltyService.Earn fEarn = earn;
            String receipt = LangContext.with(cc.lang(), () -> {
                StringBuilder lines = new StringBuilder();
                for (ServiceRecord r : priced) {
                    lines.append("• ").append(html(r.displayName())).append(": ").append(money(r.getPrice())).append("\n");
                }
                return I18n.t("bot.receipt.title") + "\n\n🚗 <b>" + html(prettyPlate(plate)) + "</b>\n" + lines
                        + (fDisc > 0 ? I18n.t("bot.receipt.priceDiscount", money(fList), fPct, money(fTotal)) + "\n"
                        : I18n.t("bot.receipt.total", money(fTotal)) + "\n")
                        + (fBonus > 0 ? I18n.t("bot.receipt.bonus", money(fBonus)) + "\n" : "")
                        + (fPaid > 0 ? I18n.t("bot.receipt.paid", money(fPaid), m.label().toLowerCase()) + "\n" : "")
                        + (fDebt > 0 ? I18n.t("bot.receipt.debt", money(fDebt), date(req.payDueDate())) + "\n" : "")
                        + LoyaltyService.receiptLine(fEarn)
                        + "\n\n📍 " + html(center.getName());
            });
            telegram.sendLater(car.getTelegramChatId(), receipt);
        }

        Boolean delivered = null;
        String msg = created ? I18n.t("ui.save.newCustomer")
                : (saved.size() > 1 ? I18n.t("ui.save.savedMany", saved.size()) : I18n.t("ui.save.savedOne"));
        if (earn != null && earn.points() > 0) {
            msg += " " + I18n.t("ui.save.cashback", points(earn.points()), points(earn.balance()))
                    + (earn.levelUp() ? " " + I18n.t("ui.save.levelUp", earn.level().label()) : "");
        }
        String readyChannel = null;
        if (p.isMaster()) {
            msg = (saved.size() > 1 ? I18n.t("ui.save.masterMany", saved.size()) : I18n.t("ui.save.masterOne")) + " " + I18n.t("ui.save.masterNote");
        } else if (req.notifyReady()) {
            readyChannel = deliverReady(saved, center);
            delivered = readyChannel != null;
            if (delivered) audit.staff(p, center, prettyPlate(plate) + " egasiga «Tayyor, keling» yubordi (" + readyChannel + ")");
            msg += " " + (delivered ? I18n.t("ui.save.readySent", readyChannel) : I18n.t("ui.save.readyFailed", prettyPhone(car.getPhone())));
        }
        if (car.getTelegramChatId() == null && !p.isMaster()) {
            msg += " " + I18n.t("ui.save.qrHint");
        }
        List<AdminRecordDto> dtos = saved.stream().map(r -> toDto(r, today, p.canContactCustomers())).toList();
        return new SaveResult(dtos.get(0), created, delivered, msg, dtos,
                p.isMaster() ? null : connectInfo(car, center), readyChannel);
    }

    /** Mijozni Telegramga ulash uchun QR + havolalar (ish saqlanganda, profilda, chekda). */
    @Transactional(readOnly = true)
    public ConnectInfo connectInfo(AdminPrincipal p, Long carId) {
        ServiceCenter center = center(p);
        Car car = cars.findById(carId).orElseThrow(() -> ApiException.notFound(I18n.t("err.customer.notFound")));
        if (!records.existsByCarAndCenter(car, center)) throw ApiException.notFound(I18n.t("err.customer.notYours"));
        return connectInfo(car, center);
    }

    private ConnectInfo connectInfo(Car car, ServiceCenter center) {
        String tg = telegramProps.deepLink(car.getToken());
        String carUrl = app.carUrl(car.getToken());
        return new ConnectInfo(car.getId(), prettyPlate(car.getPlateNumber()), car.getFullName(), prettyPhone(car.getPhone()),
                car.getTelegramChatId() != null, tg, carUrl, QrCodes.pngDataUrl(tg != null ? tg : carUrl, 260),
                center.getName(), center.getPhone() == null ? null : prettyPhone(center.getPhone()), center.getAddress());
    }

    @Transactional
    public ReadyResult sendReady(AdminPrincipal p, Long recordId) {
        ServiceCenter center = center(p);
        ServiceRecord r = records.findById(recordId)
                .filter(x -> x.getCenter() != null && x.getCenter().getId().equals(center.getId()))
                .orElseThrow(() -> ApiException.notFound(I18n.t("err.rec.notFound")));
        if (r.getReadyNotifiedAt() != null
                && Duration.between(r.getReadyNotifiedAt(), Instant.now()).toMinutes() < 2) {
            throw ApiException.badRequest(I18n.t("err.ready.justSent"));
        }
        Car car = r.getCar();
        String channel = deliverReady(r, center);
        boolean ok = channel != null;
        if (ok) audit.staff(p, center, prettyPlate(car.getPlateNumber()) + " egasiga «Tayyor, keling» yubordi (" + channel + ")");
        return new ReadyResult(ok, prettyPhone(car.getPhone()), car.getPhone(),
                ok ? I18n.t("ui.ready.sent", channel) : I18n.t("ui.ready.failed"), channel);
    }

    @Transactional(readOnly = true)
    public CenterDashboard dashboard(AdminPrincipal p) {
        ServiceCenter center = center(p);
        LocalDate today = today();
        List<ServiceRecord> all = records.findAllForCenter(center);

        if (p.isMaster()) {
            // Usta faqat o'zi kiritgan ishlarni ko'radi, telefon va statistikasiz
            List<AdminRecordDto> mine = all.stream()
                    .filter(r -> p.fullName().equals(r.getMasterName()))
                    .limit(30).map(r -> toDto(r, today, false)).toList();
            return new CenterDashboard(center.getName(), p.role().name(), null, List.of(), mine, List.of());
        }

        List<AdminRecordDto> callList = all.stream()
                .filter(r -> r.isActive() && !r.getNextDate().isAfter(today.plusDays(app.reminderDaysBefore())))
                .sorted(Comparator.comparing(ServiceRecord::getNextDate))
                .limit(100)
                .map(r -> toDto(r, today, true))
                .toList();
        List<AdminRecordDto> recent = all.stream().limit(30).map(r -> toDto(r, today, true)).toList();
        List<StaffBrief> masters = staff.findAllByCenterOrderByIdAsc(center).stream()
                .filter(s -> s.getRole() == Role.MASTER && !s.isBlocked())
                .map(s -> new StaffBrief(s.getId(), s.displayName()))
                .toList();
        return new CenterDashboard(center.getName(), p.role().name(), stats(center, all, today), callList, recent, masters);
    }

    // =====================================================================
    //  Direktor: xodimlar va jurnal
    // =====================================================================

    @Transactional(readOnly = true)
    public List<StaffDto> listStaff(AdminPrincipal p) {
        return staff.findAllByCenterOrderByIdAsc(center(p)).stream().map(this::staffDto).toList();
    }

    @Transactional
    public StaffDto addStaff(AdminPrincipal p, StaffRequest req) {
        ServiceCenter center = center(p);
        if (req.role() != Role.ADMINISTRATOR && req.role() != Role.MASTER) {
            throw ApiException.badRequest(I18n.t("err.staff.roleOnly"));
        }
        String username = req.username().trim().toLowerCase();
        if (staff.existsByUsername(username)) throw ApiException.conflict(I18n.t("err.login.taken"));
        AdminAuthService.validatePassword(req.password());

        StaffUser u = new StaffUser();
        u.setUsername(username);
        u.setFullName(trim(req.fullName()));
        u.setPasswordHash(auth.hash(req.password()));
        u.setRole(req.role());
        u.setCenter(center);
        staff.save(u);
        audit.staff(p, center, "Yangi xodim qo'shdi: " + u.displayName() + " (" + req.role().labelUz().toLowerCase() + ")");
        return staffDto(u);
    }

    @Transactional
    public StaffDto updateStaff(AdminPrincipal p, Long id, StaffUpdateRequest req) {
        ServiceCenter center = center(p);
        StaffUser u = staff.findByIdAndCenter(id, center).orElseThrow(() -> ApiException.notFound(I18n.t("err.staff.notFound")));
        if (u.getId().equals(p.staffId())) throw ApiException.badRequest(I18n.t("err.staff.self"));
        if (u.getRole() == Role.DIRECTOR) throw ApiException.badRequest(I18n.t("err.staff.director"));

        if (req.blocked() != null && req.blocked() != u.isBlocked()) {
            u.setBlocked(req.blocked());
            if (req.blocked()) sessions.deleteAllByStaff(u); // darhol barcha qurilmalardan chiqariladi
            audit.staff(p, center, u.displayName() + (req.blocked() ? "ni blokladi" : "ni faollashtirdi"));
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            AdminAuthService.validatePassword(req.newPassword());
            u.setPasswordHash(auth.hash(req.newPassword()));
            sessions.deleteAllByStaff(u);
            audit.staff(p, center, u.displayName() + " parolini tikladi");
        }
        return staffDto(u);
    }

    @Transactional(readOnly = true)
    public List<LogDto> centerLog(AdminPrincipal p) {
        return logs.findRecentForCenter(center(p), PageRequest.of(0, LOG_LIMIT)).stream().map(this::logDto).toList();
    }

    // =====================================================================
    //  Super admin
    // =====================================================================

    @Transactional(readOnly = true)
    public SuperOverview overview() {
        LocalDate today = today();
        List<CenterDto> list = new ArrayList<>();
        int total = 0, due = 0, returned = 0, active = 0;
        int expired = 0;
        for (ServiceCenter c : centers.findAllByOrderByNameAsc()) {
            CenterDto dto = centerDto(c, records.findAllForCenter(c), today);
            list.add(dto);
            total += dto.stats().totalRecords();
            due += dto.stats().due();
            returned += dto.stats().returned();
            if ("Faol".equals(dto.status())) active++;
            if (c.isActive() && "EXPIRED".equals(dto.subscription().state())) expired++;
        }
        // Obunasi tugagan/tugayotgan servislar avval, keyin faollik holati bo'yicha
        list.sort(Comparator.comparing((CenterDto d) -> subOrder(d.subscription().state()))
                .thenComparing(d -> statusOrder(d.status())).thenComparing(CenterDto::name));
        return new SuperOverview(list.size(), active, total, due > 0 ? Math.round(returned * 100f / due) : null, list,
                expired, app.subscriptionPrice());
    }

    private static int subOrder(String state) {
        return switch (state) {
            case "EXPIRED" -> 0;
            case "GRACE" -> 1;
            case "EXPIRING" -> 2;
            default -> 3;
        };
    }

    @Transactional
    public CenterDto createCenter(AdminPrincipal p, CenterRequest req) {
        String username = req.username().trim().toLowerCase();
        if (staff.existsByUsername(username)) throw ApiException.conflict(I18n.t("err.login.taken"));
        AdminAuthService.validatePassword(req.password());

        ServiceCenter c = new ServiceCenter();
        c.setName(trim(req.name()));
        c.setAddress(trim(req.address()));
        c.setPhone(req.phone() == null || req.phone().isBlank() ? null : normalizePhone(req.phone()));
        c.setSubscriptionUntil(subscription.trialUntil()); // yangi servisga sinov muddati
        centers.save(c);

        StaffUser u = new StaffUser();
        u.setUsername(username);
        u.setFullName(trim(req.directorName()));
        u.setPasswordHash(auth.hash(req.password()));
        u.setRole(Role.DIRECTOR);
        u.setCenter(c);
        staff.save(u);
        audit.staff(p, c, "Yangi servis qo'shdi: «" + c.getName() + "», direktor " + u.displayName() + " (" + username + ")");
        return centerDto(c, List.of(), today());
    }

    @Transactional
    public CenterDto updateCenter(AdminPrincipal p, Long id, CenterUpdateRequest req) {
        ServiceCenter c = centers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("err.center.notFound")));
        if (req.name() != null && !req.name().isBlank()) c.setName(trim(req.name()));
        if (req.address() != null) c.setAddress(trim(req.address()));
        if (req.phone() != null) c.setPhone(req.phone().isBlank() ? null : normalizePhone(req.phone()));
        if (req.active() != null && req.active() != c.isActive()) {
            c.setActive(req.active());
            if (!req.active()) staff.findAllByCenterOrderByIdAsc(c).forEach(sessions::deleteAllByStaff); // hammasi chiqariladi
            audit.staff(p, c, "«" + c.getName() + "» servisini " + (req.active() ? "yoqdi" : "o'chirdi"));
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            AdminAuthService.validatePassword(req.newPassword());
            StaffUser director = staff.findFirstByCenterAndRoleOrderByIdAsc(c, Role.DIRECTOR)
                    .orElseThrow(() -> ApiException.notFound(I18n.t("err.center.directorNotFound")));
            director.setPasswordHash(auth.hash(req.newPassword()));
            sessions.deleteAllByStaff(director);
            audit.staff(p, c, "«" + c.getName() + "» direktori parolini tikladi");
        }
        return centerDto(c, records.findAllForCenter(c), today());
    }

    /** Super admin jurnali: hammasi yoki bitta servis bo'yicha. */
    @Transactional(readOnly = true)
    public List<LogDto> superLog(Long centerId) {
        if (centerId == null) {
            return logs.findRecent(PageRequest.of(0, LOG_LIMIT)).stream().map(this::logDto).toList();
        }
        ServiceCenter c = centers.findById(centerId).orElseThrow(() -> ApiException.notFound(I18n.t("err.center.notFound")));
        return logs.findRecentForCenter(c, PageRequest.of(0, LOG_LIMIT)).stream().map(this::logDto).toList();
    }

    /** Shu servisdan keyin qaytib kelmagan mijozlar va sababi. */
    @Transactional(readOnly = true)
    public List<MissedDto> missed(Long centerId) {
        ServiceCenter c = centers.findById(centerId).orElseThrow(() -> ApiException.notFound(I18n.t("err.center.notFound")));
        LocalDate today = today();
        List<MissedDto> out = new ArrayList<>();
        for (ServiceRecord r : records.findAllForCenter(c)) {
            Car car = r.getCar();
            long overdue = ChronoUnit.DAYS.between(r.getNextDate(), today);
            String reason, hint;
            if (r.getReplacedAt() != null) {
                ServiceCenter by = r.getReplacedByCenter();
                if (by != null && by.getId().equals(c.getId())) continue; // qaytib kelgan
                if (by != null) {
                    reason = I18n.t("missed.elsewhere");
                    hint = I18n.t("missed.elsewhere.hint", by.getName());
                } else {
                    reason = I18n.t("missed.self");
                    hint = I18n.t("missed.self.hint");
                }
            } else if (r.isActive() && overdue > 0) {
                if (car.getTelegramChatId() == null) {
                    reason = I18n.t("missed.noTelegram");
                    hint = I18n.t("missed.noTelegram.hint");
                } else if (r.isDueReminderSent()) {
                    reason = I18n.t("missed.ignored");
                    hint = I18n.t("missed.ignored.hint");
                } else {
                    reason = I18n.t("missed.undelivered");
                    hint = I18n.t("missed.undelivered.hint");
                }
            } else {
                continue; // muddati hali kelmagan
            }
            out.add(new MissedDto(prettyPlate(car.getPlateNumber()), car.getFullName().trim(),
                    car.getPhone(), prettyPhone(car.getPhone()), r.displayName(), r.getNextDate(),
                    Math.max(overdue, 0), reason, hint));
        }
        out.sort(Comparator.comparingLong(MissedDto::daysOverdue).reversed());
        return out;
    }

    // =====================================================================
    //  Yordamchi
    // =====================================================================

    private String deliverReady(ServiceRecord r, ServiceCenter center) {
        return deliverReady(List.of(r), center);
    }

    /** «Mashinangiz tayyor» — bir tashrifdagi barcha ishlar bitta xabarda. */
    /**
     * «Mashinangiz tayyor». Telegram ulangan bo'lsa — Telegram; ulanmagan, lekin SMS yoqilgan bo'lsa — SMS,
     * ichida Telegramga ulash havolasi (keyingi eslatmalar bepul kelishi uchun).
     *
     * @return yetkazilgan kanal nomi ("Telegram" / "SMS") yoki yetkazilmasa null
     */
    private String deliverReady(List<ServiceRecord> list, ServiceCenter center) {
        Car car = list.get(0).getCar();
        return LangContext.with(car.lang(), () -> deliverReadyIn(list, center, car));
    }

    private String deliverReadyIn(List<ServiceRecord> list, ServiceCenter center, Car car) {
        if (car.getTelegramChatId() == null) {
            if (!(car.isSmsEnabled() && sms.enabled())) return null;
            String link = telegramProps.deepLink(car.getToken());
            String text = I18n.t("sms.ready", prettyPlate(car.getPlateNumber()), center.getName())
                    + (center.getPhone() == null ? "" : " " + I18n.t("sms.ready.phone", prettyPhone(center.getPhone()))) + "."
                    + (link == null ? "" : " " + I18n.t("sms.ready.link", link));
            if (!sms.send(car.getPhone(), text)) return null;
            Instant now = Instant.now();
            list.forEach(r -> r.setReadyNotifiedAt(now));
            return "SMS";
        }
        StringBuilder done = new StringBuilder();
        if (list.size() == 1) {
            ServiceRecord r = list.get(0);
            done.append(I18n.t("bot.ready.doneOne", html(r.displayName()), date(r.getNextDate()))).append("\n");
        } else {
            done.append(I18n.t("bot.ready.doneMany")).append("\n");
            for (ServiceRecord r : list) {
                done.append("• ").append(I18n.t("bot.ready.item", html(r.displayName()), date(r.getNextDate()))).append("\n");
            }
        }
        String text = I18n.t("bot.ready.title") + "\n\n"
                + "🚗 <b>" + html(prettyPlate(car.getPlateNumber())) + "</b>\n"
                + done + "\n"
                + "📍 <b>" + html(center.getName()) + "</b>\n"
                + (center.getAddress() == null || center.getAddress().isBlank() ? "" : html(center.getAddress()) + "\n")
                + (center.getPhone() == null ? "" : "📞 " + prettyPhone(center.getPhone()) + "\n")
                + "\n" + I18n.t("bot.ready.footer");
        boolean ok = telegram.sendMessage(car.getTelegramChatId(), text);
        if (!ok) return null;
        Instant now = Instant.now();
        list.forEach(r -> r.setReadyNotifiedAt(now));
        return "Telegram";
    }

    private CenterStats stats(ServiceCenter c, List<ServiceRecord> all, LocalDate today) {
        Instant monthAgo = Instant.now().minus(Duration.ofDays(30));
        Set<Long> customers = new HashSet<>(), withTelegram = new HashSet<>();
        int last30 = 0, returned = 0, elsewhere = 0, missing = 0;
        Instant last = null;
        for (ServiceRecord r : all) {
            customers.add(r.getCar().getId());
            if (r.getCar().getTelegramChatId() != null) withTelegram.add(r.getCar().getId());
            if (r.getCreatedAt().isAfter(monthAgo)) last30++;
            if (last == null || r.getCreatedAt().isAfter(last)) last = r.getCreatedAt();
            if (r.getReplacedAt() != null) {
                ServiceCenter by = r.getReplacedByCenter();
                if (by != null && by.getId().equals(c.getId())) returned++;
                else elsewhere++;
            } else if (r.isActive() && r.getNextDate().isBefore(today)) {
                missing++;
            }
        }
        int due = returned + elsewhere + missing;
        return new CenterStats(all.size(), customers.size(), last30, due, returned, elsewhere, missing,
                withTelegram.size(), due > 0 ? Math.round(returned * 100f / due) : null,
                last == null ? null : LocalDate.ofInstant(last, app.zoneId()));
    }

    private CenterDto centerDto(ServiceCenter c, List<ServiceRecord> all, LocalDate today) {
        CenterStats s = stats(c, all, today);
        LocalDate since = s.lastActivity() != null ? s.lastActivity() : LocalDate.ofInstant(c.getCreatedAt(), app.zoneId());
        int idle = (int) ChronoUnit.DAYS.between(since, today);
        String status;
        if (!c.isActive()) status = "O'chirilgan";
        else if (s.lastActivity() == null) status = idle > 3 ? "Ishlatmayapti" : "Yangi";
        else if (idle > INACTIVE_AFTER_DAYS) status = "Faol emas";
        else status = "Faol";
        Optional<StaffUser> director = staff.findFirstByCenterAndRoleOrderByIdAsc(c, Role.DIRECTOR);
        return new CenterDto(c.getId(), c.getName(), c.getAddress(), prettyPhone(c.getPhone()),
                director.map(StaffUser::displayName).orElse(null), director.map(StaffUser::getUsername).orElse(null),
                staff.countByCenter(c), c.isActive(), status, idle, s, subscription.info(c));
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "Ishlatmayapti", "Faol emas" -> 0;
            case "Yangi" -> 1;
            case "Faol" -> 2;
            default -> 3;
        };
    }

    private AdminRecordDto toDto(ServiceRecord r, LocalDate today, boolean withPhone) {
        Car car = r.getCar();
        return new AdminRecordDto(r.getId(), prettyPlate(car.getPlateNumber()), car.getFullName().trim(),
                withPhone ? car.getPhone() : null, withPhone ? prettyPhone(car.getPhone()) : null,
                car.getTelegramChatId() != null, r.displayName(), r.getDoneDate(), r.getNextDate(),
                ChronoUnit.DAYS.between(today, r.getNextDate()), r.getMileage(), r.getNote(), r.getMasterName(),
                r.isActive(), r.getReadyNotifiedAt(), withPhone ? app.carUrl(car.getToken()) : null,
                withPhone ? r.getPrice() : null, withPhone && r.getPrice() != null ? r.getPaid() : null,
                withPhone && r.getPrice() != null ? r.getRemaining() : null, withPhone ? r.getPayDueDate() : null,
                withPhone ? r.getDiscount() : null, withPhone ? r.getBonusPaid() : null, withPhone ? car.getId() : null);
    }

    private StaffDto staffDto(StaffUser u) {
        return new StaffDto(u.getId(), u.displayName(), u.getUsername(), u.getRole().name(), u.getRole().label(),
                u.isBlocked(), u.getCreatedAt());
    }

    private LogDto logDto(ActivityLog l) {
        return new LogDto(l.getCreatedAt(), l.getActorName(), l.getActorRole(),
                l.getCenter() == null ? null : l.getCenter().getName(), l.getAction());
    }

    private ServiceCenter center(AdminPrincipal p) {
        ServiceCenter c = centers.findById(p.centerId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, I18n.t("err.center.notFound")));
        if (!c.isActive()) throw new ApiException(HttpStatus.FORBIDDEN, I18n.t("err.center.disabled"));
        return c;
    }

    private LocalDate today() {
        return LocalDate.now(app.zoneId());
    }
}
