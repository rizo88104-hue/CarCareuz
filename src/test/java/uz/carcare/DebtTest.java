package uz.carcare;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** To'lov va qarz daftari: qisman to'lov, qarzni yopish, cheklovlar, mijoz bo'yicha so'ndirish. */
class DebtTest extends ApiTestBase {

    @Test
    void partialPaymentCreatesDebtAndPaymentsCloseIt() throws Exception {
        Center c = newCenter();
        String plate = uniqPlate();
        LocalDate due = LocalDate.now().plusDays(7);

        JsonNode r = addJob(c.token(), job(plate, "OIL", 300_000L, 100_000L, due));
        JsonNode rec = r.path("record");
        long recordId = rec.path("id").asLong();
        assertThat(rec.path("remaining").asLong()).isEqualTo(200_000);
        assertThat(rec.path("payDueDate").asText()).isEqualTo(due.toString());

        // Qarz daftarida qisman to'langan sifatida ko'rinadi
        JsonNode debts = get("/api/admin/center/debts?filter=ALL&period=ALL", c.token());
        JsonNode row = findRow(debts, recordId);
        assertThat(row.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(row.path("remaining").asLong()).isEqualTo(200_000);
        assertThat(row.path("overdueDays").asLong()).isZero();
        assertThat(debts.path("summary").path("totalRemaining").asLong()).isGreaterThanOrEqualTo(200_000);
        assertThat(debts.path("counts").path("PARTIAL").asInt()).isGreaterThanOrEqualTo(1);

        // Qoldiqdan ko'p to'lab bo'lmaydi
        assertThat(error(HttpMethod.POST, "/api/admin/center/records/" + recordId + "/payments", c.token(),
                Map.of("amount", 250_000, "method", "CARD"), 400)).contains("qoldig'idan");

        // Ikki to'lov bilan yopiladi
        JsonNode after1 = post("/api/admin/center/records/" + recordId + "/payments", c.token(),
                Map.of("amount", 50_000, "method", "CARD", "note", "karta"), 201);
        assertThat(after1.path("remaining").asLong()).isEqualTo(150_000);
        JsonNode after2 = post("/api/admin/center/records/" + recordId + "/payments", c.token(),
                Map.of("amount", 150_000, "method", "CASH"), 201);
        assertThat(after2.path("remaining").asLong()).isZero();
        assertThat(after2.path("status").asText()).isEqualTo("PAID");

        // Yopilgan qarzga yana to'lab bo'lmaydi
        assertThat(error(HttpMethod.POST, "/api/admin/center/records/" + recordId + "/payments", c.token(),
                Map.of("amount", 1_000), 400)).contains("qarz yo'q");

        // To'lovlar tarixi: boshlang'ich 100 000 + 50 000 + 150 000
        JsonNode history = get("/api/admin/center/records/" + recordId + "/payments", c.token());
        assertThat(history).hasSize(3);
        assertThat(history.findValues("amount").stream().mapToLong(JsonNode::asLong).sum()).isEqualTo(300_000);
        assertThat(history.findValuesAsText("methodLabel")).contains("Karta", "Naqd");

        // Filtrlar: PAID da bor, PARTIAL da yo'q
        assertThat(findRow(get("/api/admin/center/debts?filter=PAID&period=ALL", c.token()), recordId)).isNotNull();
        assertThat(findRow(get("/api/admin/center/debts?filter=PARTIAL&period=ALL", c.token()), recordId)).isNull();
    }

    @Test
    void debtRequiresDueDateAndCannotOverpay() throws Exception {
        Center c = newCenter();
        String plate = uniqPlate();
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(),
                job(plate, "OIL", 300_000L, 100_000L, null), 400)).contains("muddat");
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(),
                job(plate, "OIL", 300_000L, 100_000L, LocalDate.now().minusDays(1)), 400)).contains("o'tgan");
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(),
                job(plate, "OIL", 300_000L, 350_000L, null), 400)).contains("oshmasin");
    }

    @Test
    void customerPayDistributesAcrossOpenDebts() throws Exception {
        Center c = newCenter();
        String plate = uniqPlate();
        LocalDate due = LocalDate.now().plusDays(5);
        // Bir tashrifda ikki ish: 100 000 + 200 000, hozir 50 000 to'landi → qarz 250 000
        Map<String, Object> body = job(plate, "OIL", null, 50_000L, due);
        body.put("items", List.of(
                Map.of("type", "OIL", "nextDate", LocalDate.now().plusMonths(6).toString(), "price", 100_000),
                Map.of("type", "AIR_FILTER", "nextDate", LocalDate.now().plusMonths(12).toString(), "price", 200_000)));
        JsonNode r = post("/api/admin/center/records", c.token(), body, 201);
        assertThat(r.path("records")).hasSize(2);
        long carId = r.path("record").path("carId").asLong();

        JsonNode detail = get("/api/admin/center/customers/" + carId, c.token());
        assertThat(detail.path("debts").path("totalDebt").asLong()).isEqualTo(300_000);
        assertThat(detail.path("debts").path("paid").asLong()).isEqualTo(50_000);
        assertThat(detail.path("debts").path("remaining").asLong()).isEqualTo(250_000);
        assertThat(detail.path("debts").path("openDebts").asInt()).isEqualTo(2);

        // Umumiy qarzdan ko'p to'lab bo'lmaydi
        assertThat(error(HttpMethod.POST, "/api/admin/center/customers/" + carId + "/pay", c.token(),
                Map.of("amount", 260_000, "method", "CASH"), 400)).contains("oshmasin");

        // 120 000: avval muddati eng erta bo'lgan (ikkalasi bir xil) ishlar ketma-ket yopiladi
        detail = post("/api/admin/center/customers/" + carId + "/pay", c.token(), Map.of("amount", 120_000, "method", "TRANSFER"), 200);
        assertThat(detail.path("debts").path("remaining").asLong()).isEqualTo(130_000);
        // Qolganini yopish
        detail = post("/api/admin/center/customers/" + carId + "/pay", c.token(), Map.of("amount", 130_000, "method", "CASH"), 200);
        assertThat(detail.path("debts").path("remaining").asLong()).isZero();
        assertThat(detail.path("debts").path("openDebts").asInt()).isZero();
        assertThat(detail.path("payments").size()).isGreaterThanOrEqualTo(3); // boshlang'ich + 2 ta so'ndirish (ishlar bo'yicha bo'lingan bo'lishi mumkin)
        assertThat(error(HttpMethod.POST, "/api/admin/center/customers/" + carId + "/pay", c.token(),
                Map.of("amount", 10_000), 400)).contains("qarz yo'q");
    }

    @Test
    void priceCannotGoBelowPaid() throws Exception {
        Center c = newCenter();
        JsonNode r = addJob(c.token(), job(uniqPlate(), "OIL", 200_000L, 200_000L, null));
        long id = r.path("record").path("id").asLong();
        assertThat(error(HttpMethod.PATCH, "/api/admin/center/records/" + id + "/price", c.token(),
                Map.of("price", 150_000), 400)).contains("kam");
        JsonNode row = patch("/api/admin/center/records/" + id + "/price", c.token(), Map.of("price", 250_000, "payDueDate", LocalDate.now().plusDays(3).toString()), 200);
        assertThat(row.path("remaining").asLong()).isEqualTo(50_000);
        assertThat(row.path("status").asText()).isEqualTo("PARTIAL");
    }

    private static JsonNode findRow(JsonNode debts, long recordId) {
        for (JsonNode row : debts.path("rows")) if (row.path("recordId").asLong() == recordId) return row;
        return null;
    }
}
