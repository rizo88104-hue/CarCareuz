package uz.carcare.loyalty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import uz.carcare.model.LoyaltyLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sadoqat dasturi so'rov va javoblari. 1 ball = 1 so'm. */
public final class LoyaltyDtos {

    private LoyaltyDtos() {
    }

    // ---------- Sozlamalar ----------

    public record LevelDto(String code, String label, String icon, long from, int cashbackPct, int discountPct) {
    }

    public record ProgramDto(boolean enabled, List<LevelDto> levels, int maxRedeemPct, long welcomeBonus,
                             long birthdayBonus, int expiryDays, String updatedBy, Instant updatedAt) {
    }

    public record LevelInput(
            @NotNull(message = "{v.level}") LoyaltyLevel level,
            @NotNull(message = "{v.levelFrom}") @PositiveOrZero(message = "{v.levelFromNeg}")
            @Max(value = 100_000_000_000L, message = "{v.levelFromBig}") Long from,
            @NotNull(message = "{v.cashback}") @Min(value = 0, message = "{v.cashbackRange}")
            @Max(value = 30, message = "{v.cashbackRange}") Integer cashbackPct,
            @NotNull(message = "{v.discount}") @Min(value = 0, message = "{v.discountRange}")
            @Max(value = 50, message = "{v.discountRange}") Integer discountPct
    ) {
    }

    public record ProgramRequest(
            @NotNull Boolean enabled,
            @NotNull @Size(min = 4, max = 4, message = "{v.fourLevels}") List<@Valid LevelInput> levels,
            @NotNull @Min(value = 0, message = "{v.redeemRange}")
            @Max(value = 100, message = "{v.redeemRange}") Integer maxRedeemPct,
            @NotNull @PositiveOrZero(message = "{v.bonusNeg}") @Max(value = 10_000_000, message = "{v.bonusBig}") Long welcomeBonus,
            @NotNull @PositiveOrZero(message = "{v.bonusNeg}") @Max(value = 10_000_000, message = "{v.bonusBig}") Long birthdayBonus,
            @NotNull @Min(value = 0, message = "{v.expiryNeg}")
            @Max(value = 3650, message = "{v.expiryBig}") Integer expiryDays
    ) {
    }

    // ---------- Karta va harakatlar ----------

    public record CardDto(
            Long accountId, Long carId, String cardNumber, String cardDisplay,
            String ownerName, String plateDisplay, String phoneDisplay, boolean telegramConnected,
            String level, String levelLabel, String levelIcon, int cashbackPct, int discountPct,
            long balance, long earnedTotal, long redeemedTotal, long expiredTotal, long spendTotal,
            String nextLevelLabel, Long toNextLevel, int progressPct,
            LocalDate lastActivity, LocalDate expiresOn, Instant createdAt
    ) {
    }

    public record TxDto(Long id, Instant at, String type, String typeLabel, long points, long balanceAfter,
                        String serviceName, String note, String actorName,
                        Long carId, String ownerName, String plateDisplay) {
    }

    /** Mijoz profilidagi sadoqat bloki. */
    public record CustomerLoyalty(boolean programEnabled, boolean blacklisted, int maxRedeemPct,
                                  CardDto card, List<TxDto> transactions) {
    }

    /**
     * Ish kiritish formasi uchun (mashina raqami yozilganda).
     * willOpen — karta hali yo'q, ish saqlanganda ochiladi (welcomeBonus bilan).
     */
    public record LookupLoyalty(boolean programEnabled, boolean blacklisted, boolean willOpen,
                                String cardDisplay, String level, String levelLabel, String levelIcon,
                                long balance, int discountPct, int cashbackPct, int maxRedeemPct, long welcomeBonus) {
    }

    // ---------- Sadoqat bo'limi ----------

    public record Stats(int cards, int withBalance, long balanceTotal,
                        long earnedMonth, long redeemedMonth, long expiredMonth, int newCardsMonth) {
    }

    public record Overview(ProgramDto program, Stats stats, Map<String, Integer> levelCounts,
                           int total, List<CardDto> members, List<TxDto> recent) {
    }

    public record AdjustRequest(
            @NotNull(message = "{v.points}") @Min(value = -100_000_000, message = "{v.pointsBig}")
            @Max(value = 100_000_000, message = "{v.pointsBig}") Long points,
            @NotBlank(message = "{v.reason}") @Size(max = 200, message = "{v.reasonLong}") String note
    ) {
    }

    public record IssueAllRequest(boolean withWelcome) {
    }

    public record IssueAllResult(int created, String message) {
    }

    /** Mijoz sahifasi (car.html) va bot uchun. */
    public record PublicCard(String centerName, String cardDisplay, String level, String levelLabel, String levelIcon,
                             long balance, int cashbackPct, int discountPct,
                             String nextLevelLabel, Long toNextLevel, LocalDate expiresOn, boolean active) {
    }
}
