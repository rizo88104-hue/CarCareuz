package uz.carcare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/** Umumiy sozlamalar (application.properties dagi "app.*"). */
@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue("Asia/Tashkent") String zone,
        @DefaultValue("7") int reminderDaysBefore,
        /** Keyingi probegga shuncha km qolganda eslatish. */
        @DefaultValue("500") int reminderKmBefore,
        /** Avtoservis obunasi: oylik narx (so'm), yangi servisga sinov kunlari, muddat tugagach imtiyoz kunlari. */
        @DefaultValue("200000") long subscriptionPrice,
        @DefaultValue("14") int subscriptionTrialDays,
        @DefaultValue("3") int subscriptionGraceDays
) {
    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    /** Mashina sahifasining to'liq manzili. */
    public String carUrl(String token) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/car/" + token;
    }
}
