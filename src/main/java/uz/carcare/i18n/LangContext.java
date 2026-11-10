package uz.carcare.i18n;

import java.util.function.Supplier;

/**
 * Joriy oqimning tili: HTTP so'rovida {@link LangFilter} o'rnatadi, botda poller, fon ishlarida
 * xabar tuzilayotgan mijozning tili {@link #with} orqali vaqtincha o'rnatiladi.
 */
public final class LangContext {

    private static final ThreadLocal<Lang> CURRENT = new ThreadLocal<>();

    private LangContext() {
    }

    public static Lang get() {
        Lang l = CURRENT.get();
        return l == null ? Lang.UZ : l;
    }

    public static void set(Lang lang) {
        if (lang == null) CURRENT.remove();
        else CURRENT.set(lang);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Berilgan tilda bajaradi (masalan, mijozga ketadigan xabarni uning tilida tuzish uchun). */
    public static <T> T with(Lang lang, Supplier<T> body) {
        Lang prev = CURRENT.get();
        set(lang);
        try {
            return body.get();
        } finally {
            if (prev == null) CURRENT.remove();
            else CURRENT.set(prev);
        }
    }

    public static void run(Lang lang, Runnable body) {
        with(lang, () -> {
            body.run();
            return null;
        });
    }
}
