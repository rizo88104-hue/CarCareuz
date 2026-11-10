package uz.carcare.i18n;

/** Interfeys va bot tili. Standart — o'zbek. */
public enum Lang {
    UZ("uz"), RU("ru");

    private final String code;

    Lang(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** "ru", "ru-RU", "RU" → RU; qolgan hamma narsa (null ham) → UZ. */
    public static Lang of(String code) {
        if (code == null) return UZ;
        String c = code.trim().toLowerCase();
        return c.startsWith("ru") ? RU : UZ;
    }

    /** Telegram foydalanuvchisining language_code maydonidan. */
    public static Lang fromTelegram(String languageCode) {
        return of(languageCode);
    }
}
