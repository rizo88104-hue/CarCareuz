package uz.carcare.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Server matnlari: i18n/messages_uz.properties va i18n/messages_ru.properties.
 * Qoliplar: {0}, {1}... (MessageFormat ishlatilmaydi — o'zbek matnidagi apostrof buzilmasligi uchun).
 * Kalit topilmasa — o'zbekcha, u ham bo'lmasa kalitning o'zi qaytadi (ilova to'xtamaydi).
 */
public final class I18n {

    private static final Logger log = LoggerFactory.getLogger(I18n.class);
    private static final Map<Lang, Map<String, String>> BUNDLES = new EnumMap<>(Lang.class);

    static {
        for (Lang l : Lang.values()) BUNDLES.put(l, load("/i18n/messages_" + l.code() + ".properties"));
    }

    private I18n() {
    }

    /** Joriy oqim tilida ({@link LangContext}). */
    public static String t(String key, Object... args) {
        return t(LangContext.get(), key, args);
    }

    public static String t(Lang lang, String key, Object... args) {
        String s = BUNDLES.get(lang == null ? Lang.UZ : lang).get(key);
        if (s == null && lang != Lang.UZ) s = BUNDLES.get(Lang.UZ).get(key);
        if (s == null) {
            log.warn("i18n: kalit topilmadi: {}", key);
            return key;
        }
        return format(s, args);
    }

    /** Kalit mavjudmi (masalan, enum nomini tarjima qilishdan oldin tekshirish). */
    public static boolean has(String key) {
        return BUNDLES.get(Lang.UZ).containsKey(key);
    }

    private static String format(String s, Object... args) {
        if (args == null || args.length == 0 || s.indexOf('{') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length() + 32);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' && i + 2 < s.length() + 1) {
                int end = s.indexOf('}', i);
                if (end > i) {
                    String num = s.substring(i + 1, end);
                    if (!num.isEmpty() && num.chars().allMatch(Character::isDigit)) {
                        int idx = Integer.parseInt(num);
                        sb.append(idx < args.length && args[idx] != null ? args[idx] : "");
                        i = end;
                        continue;
                    }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static Map<String, String> load(String path) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in == null) {
                log.error("i18n fayli topilmadi: {}", path);
                return map;
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String k : p.stringPropertyNames()) map.put(k, p.getProperty(k));
        } catch (Exception e) {
            log.error("i18n faylini o'qib bo'lmadi: {}", path, e);
        }
        return map;
    }
}
