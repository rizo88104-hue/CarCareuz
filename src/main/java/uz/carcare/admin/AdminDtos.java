package uz.carcare.admin;

import jakarta.validation.constraints.*;
import uz.carcare.model.Payment;
import uz.carcare.model.ServiceType;
import uz.carcare.model.StaffUser;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    // ---------- So'rovlar ----------

    public record LoginRequest(String username, String password) {
    }

    /** 2FA: /login javobidagi challenge + Telegram'dagi 6 xonali kod. */
    public record LoginVerifyRequest(String challenge, String code) {
    }

    public record PasswordRequest(String oldPassword, String newPassword) {
    }

    /** Xavfli amalni (masalan 2FA ni o'chirish) parol bilan tasdiqlash. */
    public record PasswordConfirmRequest(String password) {
    }

    /** Super admin yangi servis qo'shadi: servis + uning direktori. */
    public record CenterRequest(
            @NotBlank(message = "{v.centerName}") @Size(max = 100) String name,
            @Size(max = 200) String address,
            String phone,
            @NotBlank(message = "{v.directorName}") @Size(max = 80, message = "{v.nameLong}") String directorName,
            @NotBlank(message = "{v.login}") @Pattern(regexp = "^[a-zA-Z0-9_.]{3,40}$", message = "{v.loginFormat}") String username,
            String password
    ) {
    }

    public record CenterUpdateRequest(Boolean active, String newPassword, String name, String address, String phone) {
    }

    /** Direktor yangi xodim qo'shadi. */
    public record StaffRequest(
            @NotBlank(message = "{v.staffName}") @Size(max = 80, message = "{v.nameLong}") String fullName,
            @NotBlank(message = "{v.login}") @Pattern(regexp = "^[a-zA-Z0-9_.]{3,40}$", message = "{v.loginFormat}") String username,
            String password,
            @NotNull(message = "{v.role}") StaffUser.Role role
    ) {
    }

    public record StaffUpdateRequest(Boolean blocked, String newPassword) {
    }

    /** Bitta tashrifdagi bitta ish (masalan: Motor moyi). Har biri alohida yozuv bo'lib saqlanadi. */
    public record RecordItem(
            ServiceType type,
            @Size(max = 80, message = "{v.nameLong}") String customName,
            LocalDate nextDate,
            @PositiveOrZero Integer nextMileage,
            /** Shu ish narxi (chegirmagacha), so'mda. Usta uchun e'tiborga olinmaydi. */
            @PositiveOrZero(message = "{v.priceNeg}") Long price
    ) {
    }

    /**
     * Servis xodimi ish kiritadi. Mashina ro'yxatda bo'lmasa, egasining ma'lumotlari ham kerak.
     * Bir tashrifda bir nechta ish: {@code items}. Eski format (bitta type/nextDate/price) ham qabul qilinadi.
     */
    public record AdminRecordRequest(
            @NotBlank(message = "{v.plate}") String plateNumber,
            @Size(max = 60) String firstName,
            @Size(max = 60) String lastName,
            String phone,
            /** Eski format: bitta ish. items bo'sh bo'lsa ishlatiladi. */
            ServiceType type,
            @Size(max = 80) String customName,
            @NotNull(message = "{v.doneDate}") LocalDate doneDate,
            LocalDate nextDate,
            @PositiveOrZero Integer mileage,
            @PositiveOrZero Integer nextMileage,
            @Size(max = 300) String note,
            /** Ishni qilgan usta (direktor/administrator tanlaydi). Usta uchun e'tiborga olinmaydi. */
            Long masterId,
            boolean notifyReady,
            /** Narx, so'mda. Usta uchun e'tiborga olinmaydi. */
            @PositiveOrZero(message = "{v.priceNeg}") Long price,
            /** Hozir to'langan summa. Narxdan kam bo'lsa, qolgani qarzga yoziladi. */
            @PositiveOrZero(message = "{v.paidNeg}") Long paid,
            Payment.Method paymentMethod,
            /** Qarzni to'lash muddati (qarz bo'lsa majburiy). */
            LocalDate payDueDate,
            /** Sadoqat chegirmasini qo'llash (null — ha). price — chegirmagacha bo'lgan narx. */
            Boolean applyDiscount,
            /** Bonus ball bilan to'lanadigan summa (butun tashrif uchun). */
            @PositiveOrZero(message = "{v.bonusNeg}") Long bonus,
            /** Bir tashrifdagi ishlar (1–10 ta). price/paid/bonus shu holda jami summaga nisbatan. */
            @Size(max = 10, message = "{v.max10}") List<@jakarta.validation.Valid RecordItem> items
    ) {
    }

    /** Qarzni so'ndirish (to'lov qabul qilish). */
    public record PaymentRequest(
            @NotNull(message = "{v.amount}") @Positive(message = "{v.amountPositive}") Long amount,
            Payment.Method method,
            @Size(max = 200) String note
    ) {
    }

    public record PaymentDto(Long id, long amount, String method, String methodLabel, String receivedBy,
                             String note, Instant createdAt) {
    }

    // ---------- Javoblar ----------

    public record MeDto(String username, String fullName, String role, String roleLabel, String centerName) {
    }

    /** Telefon raqami faqat direktor va administratorga ko'rsatiladi (ustaga null). */
    public record CarLookupDto(
            boolean exists, String plateNumber, String plateDisplay,
            String firstName, String lastName, String phone, String phoneDisplay,
            boolean telegramConnected, List<String> activeServices,
            /** Mijoz shu servisning qora ro'yxatida bo'lsa — sababi, aks holda null. */
            String blacklistReason,
            /** Sadoqat kartasi (dastur yo'q yoki usta uchun null). */
            uz.carcare.loyalty.LoyaltyDtos.LookupLoyalty loyalty,
            /** Mashinaning oxirgi ma'lum probegi, km (kiritilmagan bo'lsa null). */
            Integer currentMileage
    ) {
    }

    public record AdminRecordDto(
            Long id, String plateDisplay, String ownerName, String phone, String phoneDisplay,
            boolean telegramConnected, String serviceName, LocalDate doneDate, LocalDate nextDate,
            long daysLeft, Integer mileage, String note, String masterName, boolean active,
            Instant readyNotifiedAt, String carUrl,
            /** Narx, to'langan va qoldiq (so'mda). Ustaga null. */
            Long price, Long paid, Long remaining, LocalDate payDueDate,
            /** Sadoqat chegirmasi va ball bilan to'langan summa. Ustaga null. */
            Long discount, Long bonusPaid,
            /** Mijoz (mashina) id — profil va sadoqat kartasi uchun. Ustaga null. */
            Long carId
    ) {
    }

    /**
     * record — birinchi yozuv (eski mijozlar uchun), records — tashrifdagi barcha yozuvlar.
     * connect — mijozni Telegramga ulash uchun QR va havola (chek uchun), readyChannel — «Tayyor» qayerga ketdi.
     */
    public record SaveResult(AdminRecordDto record, boolean carCreated, Boolean readyDelivered, String message,
                             List<AdminRecordDto> records, ConnectInfo connect, String readyChannel) {
    }

    public record ReadyResult(boolean delivered, String phoneDisplay, String phone, String message, String channel) {
    }

    /**
     * Mijozni Telegram botga ulash uchun hamma narsa: QR kod (PNG data URL), bot havolasi, sahifa havolasi.
     * Ish saqlanganda ekranda ko'rsatiladi va chekka chiqariladi; mijoz QR ni skaner qilsa — bot ochiladi,
     * START va «Raqamni yuborish» bilan ulanadi.
     */
    public record ConnectInfo(
            Long carId, String plateDisplay, String ownerName, String phoneDisplay, boolean telegramConnected,
            /** t.me/Bot?start=TOKEN (bot sozlanmagan bo'lsa null). */
            String telegramLink,
            String carUrl,
            /** QR: telegramLink bo'lsa unga, aks holda carUrl ga. data:image/png;base64,... */
            String qrPng,
            String centerName, String centerPhone, String centerAddress
    ) {
    }

    public record StaffBrief(Long id, String fullName) {
    }

    /**
     * Servis paneli ma'lumotlari. Ustaga faqat o'z ishlari beriladi:
     * stats = null, callList bo'sh, telefonlar yashirilgan.
     */
    public record CenterDashboard(
            String centerName, String role, CenterStats stats,
            List<AdminRecordDto> callList, List<AdminRecordDto> recent, List<StaffBrief> masters
    ) {
    }

    /**
     * Avtoservis statistikasi.
     * due       — kelish vaqti kelgan yozuvlar (muddati o'tgan yoki allaqachon yangilangan);
     * returned  — shu servisga qaytib kelgan;
     * elsewhere — boshqa joyda qildirgan (boshqa servis yoki egasi o'zi kiritgan);
     * missing   — muddati o'tgan, hali hech qayerda qilinmagan.
     */
    public record CenterStats(
            int totalRecords, int customers, int last30Days,
            int due, int returned, int elsewhere, int missing,
            int telegramConnected, Integer returnRatePct, LocalDate lastActivity
    ) {
    }

    public record CenterDto(
            Long id, String name, String address, String phoneDisplay,
            String directorName, String directorUsername, long staffCount,
            boolean active, String status, Integer inactiveDays, CenterStats stats
    ) {
    }

    public record SuperOverview(int centers, int activeCenters, int totalRecords, Integer returnRatePct,
                                List<CenterDto> list) {
    }

    /** Qaytib kelmagan mijoz va sababi. */
    public record MissedDto(
            String plateDisplay, String ownerName, String phone, String phoneDisplay,
            String serviceName, LocalDate nextDate, long daysOverdue, String reason, String hint
    ) {
    }

    public record StaffDto(Long id, String fullName, String username, String role, String roleLabel,
                           boolean blocked, Instant createdAt) {
    }

    public record LogDto(Instant at, String actorName, String actorRole, String centerName, String action) {
    }
}
