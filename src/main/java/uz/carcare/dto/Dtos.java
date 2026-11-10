package uz.carcare.dto;

import jakarta.validation.constraints.*;
import uz.carcare.model.ServiceType;

import java.time.LocalDate;
import java.util.List;

/** API orqali keladigan va qaytadigan ma'lumotlar. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- So'rovlar ----------

    /** Validatsiya xabarlari — {v.kalit} ko'rinishida; GlobalExceptionHandler ularni joriy tilga tarjima qiladi. */
    public record RegisterRequest(
            @NotBlank(message = "{v.firstName}") @Size(max = 60, message = "{v.firstNameLong}") String firstName,
            @NotBlank(message = "{v.lastName}") @Size(max = 60, message = "{v.lastNameLong}") String lastName,
            @NotBlank(message = "{v.phone}") String phone,
            @NotBlank(message = "{v.plate}") String plateNumber,
            /** Saytda tanlangan til: "uz" yoki "ru" (ixtiyoriy). */
            String lang
    ) {
    }

    public record SettingsRequest(Boolean smsEnabled, String lang) {
    }

    /** Mijoz hozirgi probegini kiritadi (km). */
    public record MileageRequest(@NotNull(message = "{v.mileage}") @Positive(message = "{v.mileagePositive}") Integer mileage) {
    }

    public record RecordRequest(
            @NotNull(message = "{v.type}") ServiceType type,
            @Size(max = 80, message = "{v.nameLong}") String customName,
            @NotNull(message = "{v.doneDate}") LocalDate doneDate,
            @NotNull(message = "{v.nextDate}") LocalDate nextDate,
            @PositiveOrZero(message = "{v.mileageNeg}") Integer mileage,
            @PositiveOrZero(message = "{v.mileageNeg}") Integer nextMileage,
            @Size(max = 300, message = "{v.noteLong}") String note
    ) {
    }

    // ---------- Javoblar ----------

    /**
     * Ro'yxatdan o'tish / qayta kirish javobi. Telegram orqali tasdiqlash kerak bo'lsa,
     * token bo'sh keladi va confirmCode beriladi — brauzer /api/login/{code} ni so'rab turadi.
     */
    public record RegisterResponse(String token, String carUrl, boolean existing, String confirmCode, String botUrl) {
        public static RegisterResponse ready(String token, String carUrl, boolean existing) {
            return new RegisterResponse(token, carUrl, existing, null, null);
        }

        public static RegisterResponse confirm(String code, String botUrl) {
            return new RegisterResponse(null, null, true, code, botUrl);
        }
    }

    /** Kirish so'rovi holati: pending / approved / denied / expired. Havola faqat approved da. */
    public record LoginStatusDto(String status, String token, String carUrl) {
    }

    public record RecordDto(
            Long id,
            ServiceType type,
            String name,
            LocalDate doneDate,
            LocalDate nextDate,
            long daysLeft,
            Integer mileage,
            Integer nextMileage,
            /** Keyingi probeggacha qolgan km (manfiy — o'tib ketgan; probeg noma'lum bo'lsa null). */
            Integer kmLeft,
            String note,
            boolean active,
            /** Yozuvni kiritgan avtoservis nomi (mijozning o'zi kiritgan bo'lsa null). Bunday yozuvni mijoz o'chira olmaydi. */
            String serviceName
    ) {
    }

    public record CarDto(
            String token,
            String firstName,
            String lastName,
            String phone,
            String plateNumber,
            String plateDisplay,
            boolean telegramConnected,
            String telegramLink,
            boolean smsEnabled,
            boolean smsAvailable,
            int reminderDaysBefore,
            int reminderKmBefore,
            /** Hozirgi probeg va qachon yangilangani (kiritilmagan bo'lsa null). */
            Integer currentMileage,
            LocalDate mileageUpdatedOn,
            List<RecordDto> records,
            /** Servislardagi jamg'arma kartalari (sadoqat dasturi). */
            List<uz.carcare.loyalty.LoyaltyDtos.PublicCard> loyalty
    ) {
    }

    public record ServiceTypeDto(String code, String label) {
    }
}
