package uz.carcare;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Ish kiritish: yangi mijoz, yozuv, mijoz sahifasi, usta cheklovlari, validatsiya. */
class WorkFlowTest extends ApiTestBase {

    @Test
    void newCustomerJobCreatesCarRecordAndCustomerPage() throws Exception {
        Center c = newCenter();
        String plate = uniqPlate();

        JsonNode r = addJob(c.token(), job(plate, "OIL", 250_000L, 250_000L, null));
        assertThat(r.path("carCreated").asBoolean()).isTrue();
        assertThat(r.path("readyDelivered").isNull()).isTrue(); // notifyReady=false
        JsonNode rec = r.path("record");
        assertThat(rec.path("serviceName").asText()).isEqualTo("Motor moyi");
        assertThat(rec.path("price").asLong()).isEqualTo(250_000);
        assertThat(rec.path("paid").asLong()).isEqualTo(250_000);
        assertThat(rec.path("remaining").asLong()).isZero();
        assertThat(rec.path("plateDisplay").asText()).startsWith(plate.substring(0, 2));
        // Telegramga ulash uchun QR va havolalar
        JsonNode connect = r.path("connect");
        assertThat(connect.path("telegramConnected").asBoolean()).isFalse();
        assertThat(connect.path("qrPng").asText()).startsWith("data:image/png;base64,");
        assertThat(connect.path("telegramLink").asText()).startsWith("https://t.me/TestBot?start=");

        // Mijoz sahifasi: yozuv servis nomi bilan ko'rinadi, mijoz uni o'chira olmaydi
        String carUrl = connect.path("carUrl").asText();
        String token = carUrl.substring(carUrl.lastIndexOf('/') + 1);
        JsonNode car = get("/api/cars/" + token, null);
        assertThat(car.path("plateNumber").asText()).isEqualTo(plate);
        assertThat(car.path("records")).hasSize(1);
        assertThat(car.path("records").get(0).path("serviceName").asText()).isEqualTo("Servis " + c.username());
        long recordId = car.path("records").get(0).path("id").asLong();
        String err = error(HttpMethod.DELETE, "/api/cars/" + token + "/records/" + recordId, null, null, 400);
        assertThat(err).contains("avtoservisi kiritgan");

        // Lookup: mashina bor, faol servis bitta
        JsonNode lookup = get("/api/admin/center/lookup?plate=" + plate, c.token());
        assertThat(lookup.path("exists").asBoolean()).isTrue();
        assertThat(lookup.path("activeServices")).hasSize(1);

        // Dashboard: oxirgi ishlarda ko'rinadi
        JsonNode dash = get("/api/admin/center/dashboard", c.token());
        assertThat(dash.path("recent").findValuesAsText("plateDisplay")).anyMatch(p -> p.replace(" ", "").equals(plate));
    }

    @Test
    void sameTypeReplacesPreviousRecord() throws Exception {
        Center c = newCenter();
        String plate = uniqPlate();
        addJob(c.token(), job(plate, "OIL", null, null, null));
        JsonNode second = addJob(c.token(), job(plate, "OIL", null, null, null));
        assertThat(second.path("carCreated").asBoolean()).isFalse();
        long carId = second.path("record").path("carId").asLong();
        JsonNode detail = get("/api/admin/center/customers/" + carId, c.token());
        // Tarixda ikkala yozuv, faqat yangisi faol
        assertThat(detail.path("history")).hasSize(2);
        assertThat(detail.path("history").findValues("active").stream().filter(JsonNode::asBoolean).count()).isEqualTo(1);
        assertThat(detail.path("activeServices")).hasSize(1);
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        Center c = newCenter();
        String plate = uniqPlate();

        Map<String, Object> future = job(plate, "OIL", null, null, null);
        future.put("doneDate", LocalDate.now().plusDays(1).toString());
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(), future, 400)).contains("kelajakda");

        Map<String, Object> dup = job(plate, "OIL", null, null, null);
        dup.put("items", List.of(
                Map.of("type", "OIL", "nextDate", LocalDate.now().plusMonths(6).toString()),
                Map.of("type", "OIL", "nextDate", LocalDate.now().plusMonths(6).toString())));
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(), dup, 400)).contains("ikki marta");

        Map<String, Object> other = job(plate, "OTHER", null, null, null);
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(), other, 400)).contains("Boshqa");

        Map<String, Object> noName = job(plate, "OIL", null, null, null);
        noName.put("firstName", "");
        assertThat(error(HttpMethod.POST, "/api/admin/center/records", c.token(), noName, 400)).contains("ismini kiriting");

        // Ruscha xato — X-Lang orqali
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/center/records")
                        .header("Authorization", "Bearer " + c.token()).header("X-Lang", "ru")
                        .contentType("application/json").content(json.writeValueAsString(future)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("будущем");
    }

    @Test
    void masterJobIsSavedWithoutMoneyAndDirectorSetsPriceLater() throws Exception {
        Center c = newCenter();
        String masterUser = uniq("usta");
        post("/api/admin/center/staff", c.token(), Map.of("fullName", "Usta Ali", "role", "MASTER",
                "username", masterUser, "password", "Usta-Pass-1"), 201);
        String masterToken = login(masterUser, "Usta-Pass-1");

        String plate = uniqPlate();
        Map<String, Object> body = job(plate, "BRAKE_PADS", 400_000L, 400_000L, null); // usta narx yuborsa ham e'tiborga olinmaydi
        JsonNode r = post("/api/admin/center/records", masterToken, body, 201);
        assertThat(r.path("record").path("price").isNull()).isTrue();
        assertThat(r.path("message").asText()).contains("administrator");
        assertThat(r.path("connect").isNull()).isTrue(); // ustaga QR/chek ko'rsatilmaydi

        // Usta xodim qo'sha olmaydi, direktor bo'limi yopiq
        call(HttpMethod.GET, "/api/admin/center/staff", masterToken, null, 403);

        // Direktor narx qo'yadi: qarz bilan (to'lov yo'q) → muddat shart
        long recordId = r.path("record").path("id").asLong();
        assertThat(error(HttpMethod.PATCH, "/api/admin/center/records/" + recordId + "/price", c.token(),
                Map.of("price", 400_000), 400)).contains("muddat");
        JsonNode row = patch("/api/admin/center/records/" + recordId + "/price", c.token(),
                Map.of("price", 400_000, "payDueDate", LocalDate.now().plusDays(10).toString()), 200);
        assertThat(row.path("price").asLong()).isEqualTo(400_000);
        assertThat(row.path("remaining").asLong()).isEqualTo(400_000);
        assertThat(row.path("status").asText()).isEqualTo("UNPAID");
    }

    @Test
    void customerRegistersAddsRecordAndMileage() throws Exception {
        String plate = uniqPlate();
        JsonNode reg = post("/api/cars", null, Map.of("firstName", "Ali", "lastName", "Valiyev",
                "phone", uniqPhone(), "plateNumber", plate, "lang", "ru"), 200);
        String token = reg.path("token").asText();
        assertThat(reg.path("existing").asBoolean()).isFalse();

        Map<String, Object> rec = new HashMap<>();
        rec.put("type", "OIL");
        rec.put("doneDate", LocalDate.now().toString());
        rec.put("nextDate", LocalDate.now().plusMonths(6).toString());
        rec.put("mileage", 10_000);
        rec.put("nextMileage", 10_400);
        JsonNode car = post("/api/cars/" + token + "/records", null, rec, 201);
        assertThat(car.path("currentMileage").asInt()).isEqualTo(10_000);
        assertThat(car.path("records").get(0).path("kmLeft").asInt()).isEqualTo(400);

        car = post("/api/cars/" + token + "/mileage", null, Map.of("mileage", 10_350), 200);
        assertThat(car.path("records").get(0).path("kmLeft").asInt()).isEqualTo(50);
        assertThat(error(HttpMethod.POST, "/api/cars/" + token + "/mileage", null, Map.of("mileage", 9_000), 400)).contains("kam");

        // Qayta kirish (Telegram ulanmagan): o'sha token qaytadi
        JsonNode again = post("/api/cars", null, Map.of("firstName", "X", "lastName", "Y",
                "phone", reg.path("carUrl").asText().isEmpty() ? "" : get("/api/cars/" + token, null).path("phone").asText(),
                "plateNumber", plate), 200);
        assertThat(again.path("existing").asBoolean()).isTrue();
        assertThat(again.path("token").asText()).isEqualTo(token);

        // Havolani yangilash: eski token ishlamaydi
        JsonNode rotated = post("/api/cars/" + token + "/rotate", null, null, 200);
        assertThat(rotated.path("token").asText()).isNotEqualTo(token);
        call(HttpMethod.GET, "/api/cars/" + token, null, null, 404);
        call(HttpMethod.GET, "/api/cars/" + rotated.path("token").asText(), null, null, 200);
    }
}
