package uz.carcare;

import org.junit.jupiter.api.Test;
import uz.carcare.i18n.I18n;
import uz.carcare.i18n.Lang;
import uz.carcare.i18n.LangContext;
import uz.carcare.model.ServiceType;
import uz.carcare.reminder.MileageService;
import uz.carcare.util.Formats;
import uz.carcare.util.IpAllowList;
import uz.carcare.web.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spring'siz tez birlik testlar: formatlash, IP ro'yxati, probeg matni, tarjima. */
class UnitTests {

    @Test
    void phoneAndPlateNormalization() {
        assertThat(Formats.normalizePhone("+998 (90) 123-45-67")).isEqualTo("998901234567");
        assertThat(Formats.normalizePhone("901234567")).isEqualTo("998901234567");
        assertThatThrownBy(() -> Formats.normalizePhone("12345")).isInstanceOf(ApiException.class);
        assertThat(Formats.normalizePlate("01 a 123 bc")).isEqualTo("01A123BC");
        assertThat(Formats.prettyPlate("01A123BC")).isEqualTo("01 A 123 BC");
        assertThatThrownBy(() -> Formats.normalizePlate("AB")).isInstanceOf(ApiException.class);
    }

    @Test
    void ipAllowList() {
        IpAllowList empty = new IpAllowList("");
        assertThat(empty.enabled()).isFalse();
        assertThat(empty.allows("1.2.3.4")).isTrue();

        IpAllowList list = new IpAllowList("84.54.1.2, 10.0.0.0/8, ::1, bad-entry");
        assertThat(list.enabled()).isTrue();
        assertThat(list.invalidEntries()).containsExactly("bad-entry");
        assertThat(list.allows("84.54.1.2")).isTrue();
        assertThat(list.allows("84.54.1.3")).isFalse();
        assertThat(list.allows("10.200.3.4")).isTrue();
        assertThat(list.allows("11.0.0.1")).isFalse();
        assertThat(list.allows("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(list.allows(null)).isFalse();
    }

    @Test
    void mileageParsing() {
        assertThat(MileageService.parseKm("123456")).isEqualTo(123456);
        assertThat(MileageService.parseKm("/probeg 123 456")).isEqualTo(123456);
        assertThat(MileageService.parseKm("01A123BC 98700 km")).isEqualTo(98700);
        assertThat(MileageService.parseKm("salom")).isNull();
        assertThat(MileageService.parseKm("12")).isNull();
        assertThat(MileageService.km(123456)).isEqualTo("123 456 km");
    }

    @Test
    void i18nFormatsAndFallsBack() {
        assertThat(I18n.t(Lang.UZ, "bot.status.daysLeft", 5)).isEqualTo("5 kun qoldi");
        assertThat(I18n.t(Lang.RU, "bot.status.daysLeft", 5)).isEqualTo("осталось 5 дн.");
        assertThat(I18n.t(Lang.RU, "type.OIL")).isEqualTo("Моторное масло");
        assertThat(I18n.t(Lang.RU, "no.such.key")).isEqualTo("no.such.key");
        // Apostrofli o'zbek matni buzilmaydi
        assertThat(I18n.t(Lang.UZ, "err.rec.centerOwned", "Avto Lider")).contains("«Avto Lider»").contains("o'chirib bo'lmaydi");

        // Enum nomlari joriy oqim tiliga qaraydi
        assertThat(ServiceType.OIL.getLabel()).isEqualTo("Motor moyi");
        LangContext.run(Lang.RU, () -> assertThat(ServiceType.OIL.getLabel()).isEqualTo("Моторное масло"));
        assertThat(ServiceType.OIL.getLabel()).isEqualTo("Motor moyi");
        assertThat(Lang.of("ru-RU")).isEqualTo(Lang.RU);
        assertThat(Lang.of(null)).isEqualTo(Lang.UZ);
    }
}
