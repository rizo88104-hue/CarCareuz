package uz.carcare;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sadoqat dasturi: karta ochilishi, keshbek, ball bilan to'lov, chegirma, darajalar, tuzatish, sozlamalar. */
class LoyaltyTest extends ApiTestBase {

    @Test
    void firstPaidJobOpensCardWithWelcomeBonusAndCashback() throws Exception {
        Center c = newCenter();
        JsonNode prog = call(HttpMethod.PUT, "/api/admin/center/loyalty/settings", c.token(), loyaltySettings(true), 200);
        assertThat(prog.path("enabled").asBoolean()).isTrue();

        JsonNode r = addJob(c.token(), job(uniqPlate(), "OIL", 500_000L, 500_000L, null));
        assertThat(r.path("message").asText()).contains("Keshbek");
        long carId = r.path("record").path("carId").asLong();

        JsonNode loy = get("/api/admin/center/customers/" + carId + "/loyalty", c.token());
        assertThat(loy.path("programEnabled").asBoolean()).isTrue();
        JsonNode card = loy.path("card");
        assertThat(card.isNull()).isFalse();
        // Xush kelibsiz 10 000 + 3% keshbek (Bronza) 500 000 dan = 15 000
        assertThat(card.path("balance").asLong()).isEqualTo(25_000);
        assertThat(card.path("level").asText()).isEqualTo("BRONZE");
        assertThat(card.path("spendTotal").asLong()).isEqualTo(500_000);
        assertThat(loy.path("transactions").findValuesAsText("type")).contains("WELCOME", "CASHBACK");
        assertThat(card.path("cardDisplay").asText()).isNotBlank();
    }

    @Test
    void bonusRedemptionIsCappedAndReducesBalance() throws Exception {
        Center c = newCenter();
        call(HttpMethod.PUT, "/api/admin/center/loyalty/settings", c.token(), loyaltySettings(true), 200);
        String plate = uniqPlate();
        addJob(c.token(), job(plate, "OIL", 500_000L, 500_000L, null)); // balans 25 000

        // 100 000 so'mlik ish: ball bilan ko'pi bilan 50% = 50 000, lekin balans 25 000 → 30 000 rad etiladi
        Map<String, Object> over = job(plate, "AIR_FILTER", 100_000L, 70_000L, null);
        over.put("bonus", 30_000);
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(), over, 400)).contains("Ball bilan");

        // 20 000 ball + 80 000 pul = to'liq
        Map<String, Object> ok = job(plate, "AIR_FILTER", 100_000L, 80_000L, null);
        ok.put("bonus", 20_000);
        JsonNode r = post("/api/admin/center/records", c.token(), ok, 201);
        JsonNode rec = r.path("record");
        assertThat(rec.path("bonusPaid").asLong()).isEqualTo(20_000);
        assertThat(rec.path("paid").asLong()).isEqualTo(100_000); // ball ham to'lov hisobiga kiradi
        assertThat(rec.path("remaining").asLong()).isZero();

        long carId = rec.path("carId").asLong();
        JsonNode card = get("/api/admin/center/customers/" + carId + "/loyalty", c.token()).path("card");
        // 25 000 − 20 000 + 3% × 80 000 (pul qismidan) = 7 400; balldan keshbek yozilmaydi
        assertThat(card.path("balance").asLong()).isEqualTo(7_400);
        assertThat(card.path("redeemedTotal").asLong()).isEqualTo(20_000);
    }

    @Test
    void reachingSilverGivesDiscountOnNextJob() throws Exception {
        Center c = newCenter();
        call(HttpMethod.PUT, "/api/admin/center/loyalty/settings", c.token(), loyaltySettings(true), 200);
        String plate = uniqPlate();

        // 3 000 000 so'm pul bilan to'landi → Kumush (chegara 3 000 000)
        JsonNode r1 = addJob(c.token(), job(plate, "TIRES", 3_000_000L, 3_000_000L, null));
        assertThat(r1.path("message").asText()).contains("Kumush");
        long carId = r1.path("record").path("carId").asLong();
        JsonNode card = get("/api/admin/center/customers/" + carId + "/loyalty", c.token()).path("card");
        assertThat(card.path("level").asText()).isEqualTo("SILVER");
        assertThat(card.path("discountPct").asInt()).isEqualTo(3);
        assertThat(card.path("cashbackPct").asInt()).isEqualTo(5);

        // Keyingi ish 100 000: chegirma 3% → 97 000 to'lanadi, keshbek 5% × 97 000
        JsonNode r2 = addJob(c.token(), job(plate, "OIL", 100_000L, 97_000L, null));
        JsonNode rec = r2.path("record");
        assertThat(rec.path("price").asLong()).isEqualTo(97_000);
        assertThat(rec.path("discount").asLong()).isEqualTo(3_000);
        assertThat(rec.path("remaining").asLong()).isZero();

        // Chegirmani o'chirib ham kiritish mumkin
        Map<String, Object> noDisc = job(plate, "CABIN_FILTER", 100_000L, 100_000L, null);
        noDisc.put("applyDiscount", false);
        assertThat(post("/api/admin/center/records", c.token(), noDisc, 201).path("record").path("discount").asLong()).isZero();

        // Lookup ish formasi uchun kartani ko'rsatadi
        JsonNode lookup = get("/api/admin/center/lookup?plate=" + plate, c.token());
        assertThat(lookup.path("loyalty").path("level").asText()).isEqualTo("SILVER");
        assertThat(lookup.path("loyalty").path("programEnabled").asBoolean()).isTrue();
    }

    @Test
    void directorAdjustsPointsWithinBalance() throws Exception {
        Center c = newCenter();
        call(HttpMethod.PUT, "/api/admin/center/loyalty/settings", c.token(), loyaltySettings(true), 200);
        long carId = addJob(c.token(), job(uniqPlate(), "OIL", 100_000L, 100_000L, null)).path("record").path("carId").asLong();
        // 10 000 + 3 000 = 13 000
        JsonNode loy = post("/api/admin/center/customers/" + carId + "/loyalty/adjust", c.token(),
                Map.of("points", 5_000, "note", "kompensatsiya"), 200);
        assertThat(loy.path("card").path("balance").asLong()).isEqualTo(18_000);
        assertThat(error(HttpMethod.POST, "/api/admin/center/customers/" + carId + "/loyalty/adjust", c.token(),
                Map.of("points", -20_000, "note", "xato"), 400)).contains("Balansda");
        assertThat(error(HttpMethod.POST, "/api/admin/center/customers/" + carId + "/loyalty/adjust", c.token(),
                Map.of("points", 0, "note", "x"), 400)).contains("0");
        loy = post("/api/admin/center/customers/" + carId + "/loyalty/adjust", c.token(), Map.of("points", -18_000, "note", "yopish"), 200);
        assertThat(loy.path("card").path("balance").asLong()).isZero();
    }

    @Test
    void disabledProgramOpensNoCardAndRejectsBonus() throws Exception {
        Center c = newCenter(); // dastur yoqilmagan
        String plate = uniqPlate();
        long carId = addJob(c.token(), job(plate, "OIL", 200_000L, 200_000L, null)).path("record").path("carId").asLong();
        JsonNode loy = get("/api/admin/center/customers/" + carId + "/loyalty", c.token());
        assertThat(loy.path("programEnabled").asBoolean()).isFalse();
        assertThat(loy.path("card").isNull()).isTrue();

        Map<String, Object> withBonus = job(plate, "AIR_FILTER", 100_000L, 90_000L, null);
        withBonus.put("bonus", 10_000);
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(), withBonus, 400)).contains("Ball bilan");
        assertThat(error(HttpMethod.POST, "/api/admin/center/customers/" + carId + "/loyalty/card", c.token(), null, 400))
                .contains("Avval sadoqat dasturini yoqing");
    }

    @Test
    void settingsAreValidated() throws Exception {
        Center c = newCenter();
        Map<String, Object> bad = new HashMap<>(loyaltySettings(true));
        List<Map<String, Object>> levels = new ArrayList<>();
        levels.add(Map.of("level", "BRONZE", "from", 0, "cashbackPct", 5, "discountPct", 0));
        levels.add(Map.of("level", "SILVER", "from", 3_000_000, "cashbackPct", 3, "discountPct", 3)); // keshbek kamaydi
        levels.add(Map.of("level", "GOLD", "from", 10_000_000, "cashbackPct", 7, "discountPct", 5));
        levels.add(Map.of("level", "PLATINUM", "from", 25_000_000, "cashbackPct", 10, "discountPct", 10));
        bad.put("levels", levels);
        assertThat(error(HttpMethod.PUT, "/api/admin/center/loyalty/settings", c.token(), bad, 400)).contains("keshbek");

        Map<String, Object> expiry = new HashMap<>(loyaltySettings(true));
        expiry.put("expiryDays", 10);
        assertThat(error(HttpMethod.PUT, "/api/admin/center/loyalty/settings", c.token(), expiry, 400)).contains("30 kun");

        Map<String, Object> pct = new HashMap<>(loyaltySettings(true));
        pct.put("maxRedeemPct", 150);
        assertThat(error(HttpMethod.PUT, "/api/admin/center/loyalty/settings", c.token(), pct, 400)).contains("0–100%");
    }
}
