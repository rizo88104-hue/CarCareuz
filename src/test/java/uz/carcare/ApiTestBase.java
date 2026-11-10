package uz.carcare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API testlari uchun asos: xotiradagi H2, Telegram va SMS o'chiq, super admin paroli ma'lum.
 * Barcha test sinflari bir xil kontekstdan foydalanadi (Spring keshlaydi), shuning uchun har test
 * o'ziga xos login/raqamlar ishlatadi ({@link #uniq}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:carcare-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "superadmin.username=superadmin",
        "superadmin.password=Test-Pass-12345",
        "superadmin.allowed-ips=",
        "telegram.bot-token=",
        "telegram.bot-username=TestBot",
        "sms.enabled=false",
        "app.base-url=http://localhost:8080",
        "app.subscription-price=200000",
        "app.subscription-trial-days=14",
        "app.subscription-grace-days=3"
})
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;

    protected static final String SUPER_PASSWORD = "Test-Pass-12345";

    // ---------- HTTP yordamchilar ----------

    /** So'rov yuboradi, kutilgan statusni tekshiradi, javob JSON ini qaytaradi (bo'sh bo'lsa null). */
    protected JsonNode call(HttpMethod method, String path, String token, Object body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.request(method, path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Lang", "uz");
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.content(json.writeValueAsString(body));
        MvcResult res = mvc.perform(req).andReturn();
        String content = res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(res.getResponse().getStatus())
                .as("%s %s → %s", method, path, content)
                .isEqualTo(expectedStatus);
        return content.isBlank() ? null : json.readTree(content);
    }

    protected JsonNode get(String path, String token) throws Exception {
        return call(HttpMethod.GET, path, token, null, 200);
    }

    protected JsonNode post(String path, String token, Object body, int expected) throws Exception {
        return call(HttpMethod.POST, path, token, body, expected);
    }

    protected JsonNode patch(String path, String token, Object body, int expected) throws Exception {
        return call(HttpMethod.PATCH, path, token, body, expected);
    }

    /** Xato javobidagi "error" matni. */
    protected String error(HttpMethod method, String path, String token, Object body, int expectedStatus) throws Exception {
        JsonNode n = call(method, path, token, body, expectedStatus);
        assertThat(n).isNotNull();
        return n.path("error").asText();
    }

    // ---------- Ma'lumot yordamchilari ----------

    protected static String uniq(String prefix) {
        return prefix + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    /** O'zbekiston raqami formatida tasodifiy mashina raqami: 01A123BC. */
    protected static String uniqPlate() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return String.format("%02d%c%03d%c%c", r.nextInt(1, 96), (char) ('A' + r.nextInt(26)), r.nextInt(100, 1000),
                (char) ('A' + r.nextInt(26)), (char) ('A' + r.nextInt(26)));
    }

    protected static String uniqPhone() {
        return "+99890" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
    }

    protected String superToken() throws Exception {
        return post("/api/admin/login", null, Map.of("username", "superadmin", "password", SUPER_PASSWORD), 200).path("token").asText();
    }

    protected String login(String username, String password) throws Exception {
        return post("/api/admin/login", null, Map.of("username", username, "password", password), 200).path("token").asText();
    }

    /** Yangi avtoservis + direktor. @return {centerId, directorToken} */
    protected Center newCenter() throws Exception {
        String user = uniq("dir");
        JsonNode c = post("/api/admin/super/centers", superToken(), Map.of(
                "name", "Servis " + user, "address", "Toshkent", "phone", uniqPhone(),
                "directorName", "Dir Ektor", "username", user, "password", "Dir-Pass-123"), 201);
        return new Center(c.path("id").asLong(), login(user, "Dir-Pass-123"), user);
    }

    public record Center(long id, String token, String username) {
    }

    /** Bir ishdan iborat tashrif so'rovi (narx bilan). */
    protected Map<String, Object> job(String plate, String type, Long price, Long paid, LocalDate payDue) {
        Map<String, Object> item = new HashMap<>();
        item.put("type", type);
        item.put("nextDate", LocalDate.now().plusMonths(6).toString());
        item.put("price", price);
        Map<String, Object> body = new HashMap<>();
        body.put("plateNumber", plate);
        body.put("firstName", "Test");
        body.put("lastName", "Mijoz");
        body.put("phone", uniqPhone());
        body.put("items", List.of(item));
        body.put("doneDate", LocalDate.now().toString());
        body.put("notifyReady", false);
        body.put("paid", paid);
        body.put("paymentMethod", "CASH");
        if (payDue != null) body.put("payDueDate", payDue.toString());
        return body;
    }

    protected JsonNode addJob(String directorToken, Map<String, Object> body) throws Exception {
        return post("/api/admin/center/records", directorToken, body, 201);
    }

    /** Standart sadoqat dasturi sozlamalari (yoqilgan). */
    protected Map<String, Object> loyaltySettings(boolean enabled) {
        return Map.of(
                "enabled", enabled,
                "levels", List.of(
                        Map.of("level", "BRONZE", "from", 0, "cashbackPct", 3, "discountPct", 0),
                        Map.of("level", "SILVER", "from", 3_000_000, "cashbackPct", 5, "discountPct", 3),
                        Map.of("level", "GOLD", "from", 10_000_000, "cashbackPct", 7, "discountPct", 5),
                        Map.of("level", "PLATINUM", "from", 25_000_000, "cashbackPct", 10, "discountPct", 10)),
                "maxRedeemPct", 50, "welcomeBonus", 10_000, "birthdayBonus", 20_000, "expiryDays", 365);
    }
}
