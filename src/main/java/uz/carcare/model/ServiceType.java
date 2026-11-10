package uz.carcare.model;

/** Servis turlari. Yangi tur kerak bo'lsa shu yerga qo'shiladi. */
public enum ServiceType {
    OIL("Motor moyi"),
    OIL_FILTER("Moy filtri"),
    AIR_FILTER("Havo filtri"),
    CABIN_FILTER("Salon filtri"),
    FUEL_FILTER("Yoqilg'i filtri"),
    BRAKE_PADS("Tormoz kolodkasi"),
    ANTIFREEZE("Antifriz"),
    TRANSMISSION_OIL("Karobka moyi"),
    SPARK_PLUGS("Svecha"),
    TIRES("Ballon (shina)"),
    INSURANCE("Sug'urta"),
    INSPECTION("Texosmotr"),
    GAS_CYLINDER("Gaz ballon tekshiruvi"),
    OTHER("Boshqa");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    /** Joriy tilda (LangContext): o'zbek yoki rus. */
    public String getLabel() {
        return uz.carcare.i18n.I18n.t("type." + name());
    }

    public String getLabelUz() {
        return label;
    }
}
