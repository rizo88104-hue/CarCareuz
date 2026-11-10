package uz.carcare;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Obuna: sinov muddati, to'lov bilan uzaytirish, imtiyoz, bloklash va ochilish. */
class SubscriptionTest extends ApiTestBase {

    @Test
    void trialPaymentGraceAndBlocking() throws Exception {
        Center c = newCenter();
        String sa = superToken();
        String path = "/api/admin/super/centers/" + c.id() + "/subscription";

        JsonNode d = get(path, sa);
        assertThat(d.path("info").path("state").asText()).isEqualTo("ACTIVE");
        assertThat(d.path("info").path("until").asText()).isEqualTo(LocalDate.now().plusDays(14).toString());
        assertThat(d.path("info").path("monthlyPrice").asLong()).isEqualTo(200_000);

        // 2 oy to'lov: summa avtomatik 400 000, muddat sinov tugashidan boshlab uzayadi
        d = post(path, sa, Map.of("months", 2, "note", "naqd"), 200);
        assertThat(d.path("info").path("until").asText()).isEqualTo(LocalDate.now().plusDays(14).plusMonths(2).toString());
        assertThat(d.path("payments").get(0).path("amount").asLong()).isEqualTo(400_000);
        assertThat(get("/api/admin/me", c.token()).path("subscription").path("state").asText()).isEqualTo("ACTIVE");

        // Kecha tugagan → imtiyoz, hali kiradi
        d = patch(path, sa, Map.of("until", LocalDate.now().minusDays(1).toString()), 200);
        assertThat(d.path("info").path("state").asText()).isEqualTo("GRACE");
        assertThat(get("/api/admin/me", c.token()).path("subscription").path("state").asText()).isEqualTo("GRACE");

        // 10 kun oldin tugagan → bloklangan: mavjud sessiya ham, yangi kirish ham rad etiladi
        d = patch(path, sa, Map.of("until", LocalDate.now().minusDays(10).toString()), 200);
        assertThat(d.path("info").path("state").asText()).isEqualTo("EXPIRED");
        assertThat(error(HttpMethod.GET, "/api/admin/me", c.token(), null, 403)).contains("200 000 so'm");
        assertThat(error(HttpMethod.POST, "/api/admin/login", null, Map.of("username", c.username(), "password", "Dir-Pass-123"), 403))
                .contains("obuna muddati tugagan");
        assertThat(get("/api/admin/super/overview", sa).path("expiredCenters").asInt()).isGreaterThanOrEqualTo(1);

        // To'lov qabul qilingach darhol ochiladi (bugundan 1 oy)
        d = post(path, sa, Map.of("months", 1), 200);
        assertThat(d.path("info").path("state").asText()).isEqualTo("ACTIVE");
        assertThat(d.path("info").path("until").asText()).isEqualTo(LocalDate.now().plusMonths(1).toString());
        login(c.username(), "Dir-Pass-123");

        assertThat(error(HttpMethod.POST, path, sa, Map.of("months", 99), 400)).contains("1 dan 36");
        // Direktor obunani boshqara olmaydi
        call(HttpMethod.GET, path, c.token(), null, 403);
    }
}
