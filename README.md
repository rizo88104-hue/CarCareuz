# CarCare — mashina servis eslatmalari

Foydalanuvchi ism, familiya, telefon va mashina raqamini kiritadi → mashina uchun
shaxsiy sahifa ochiladi → u yerda moy (va boshqa servislar) qachon almashtirilgani
va keyingi sana belgilanadi → **muddatga 1 hafta qolganda** va **muddat kuni**
Telegram bot (va ixtiyoriy SMS) orqali eslatma keladi.

## Texnologiyalar

- **Backend:** Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Scheduling)
- **Baza:** H2 (lokal, sozlash shart emas) yoki PostgreSQL (server)
- **Frontend:** oddiy HTML + CSS + JavaScript (`src/main/resources/static`)
- **Telegram:** Bot API, long polling (domen/HTTPS shart emas)
- **SMS:** Eskiz.uz

## Loyiha tuzilmasi

```
src/main/java/uz/carcare/
├── CarCareApplication.java      ← kirish nuqtasi
├── config/                      ← sozlamalar (app, telegram, sms)
├── model/                       ← Car, ServiceRecord, ServiceType
├── repo/                        ← baza bilan ishlash
├── dto/                         ← API so'rov/javoblari
├── web/                         ← REST API, CarService, xatoliklar
├── telegram/                    ← TelegramClient, TelegramBotPoller (bot)
├── sms/                         ← SmsService (Eskiz)
├── reminder/                    ← ReminderService (har kuni 09:00), NotificationService
└── util/                        ← telefon/raqam formatlash
src/main/resources/
├── application.properties
└── static/  index.html (ro'yxatdan o'tish), car.html (mashina sahifasi), app.css
```

## 1. Telegram bot yaratish

1. Telegramda **@BotFather** ga yozing → `/newbot`
2. Nom bering (masalan `CarCare`) va username (masalan `CarCareUzBot`, oxiri `bot` bilan tugashi shart)
3. BotFather bergan **token** ni saqlab qo'ying (`123456:ABC...`)
4. Ixtiyoriy: `/setcommands` → botingizni tanlab quyidagini yuboring:
   ```
   holat - Mashinalarim va yaqin servislar
   ```

## 2. Lokal ishga tushirish

Kerak: **JDK 21** va **Maven 3.9+** (yoki IntelliJ IDEA — u Maven ni o'zi yuklaydi).

```bash
# Linux / macOS
export TELEGRAM_BOT_TOKEN="123456:ABC..."
export TELEGRAM_BOT_USERNAME="CarCareUzBot"
mvn spring-boot:run
```

```powershell
# Windows PowerShell
$env:TELEGRAM_BOT_TOKEN="123456:ABC..."
$env:TELEGRAM_BOT_USERNAME="CarCareUzBot"
mvn spring-boot:run
```

Brauzerda oching: **http://localhost:8080**

IntelliJ da: loyihani oching → `CarCareApplication` ni Run qiling → Run Configuration
ichida *Environment variables* ga yuqoridagi ikki o'zgaruvchini qo'shing.

> Token berilmasa ham sayt ishlaydi — bot o'chiq turadi, xabarlar faqat konsolga yoziladi.

## 3. Qanday ishlaydi

1. `/` — foydalanuvchi ma'lumot kiritadi → `/car/{maxfiy-kalit}` sahifasi ochiladi.
   Shu raqam + telefon bilan qayta kirsa, o'sha sahifaga qaytadi.
2. Sahifada **"Telegramga ulash"** → `t.me/Bot?start=KALIT` ochiladi → START → bot
   **"📱 Raqamni yuborish"** tugmasini ko'rsatadi. Telegram raqami saytdagi raqamga mos
   kelsagina mashina ulanadi — bu SMS kodsiz, **bepul tasdiqlash**.
   **Bitta bot hamma foydalanuvchiga xizmat qiladi.**
3. **"+ Qo'shish"** → servis turi, almashtirilgan sana, keyingi sana (+1/+3/+6 oy tugmalari),
   probeg va izoh. Shu turdagi yangi yozuv qo'shilsa, eskisi **Tarix** ga o'tadi.
   Mavjud yozuvdagi **"Almashtirdim"** tugmasi formani tayyor holda ochadi.
4. **Har kuni 09:00 (Toshkent)** da tekshiriladi:
   - muddatga ≤ 7 kun qolgan → "N kun qoldi" eslatmasi (bir marta);
   - muddat kuni (yoki o'tib ketgan) → "Bugun almashtirish kuni" (bir marta).
   Yangi yozuv qo'shilganda ham darhol tekshiriladi. Xabar yetkazilmasa
   (masalan bot hali ulanmagan), ertasi kuni yana urinadi.
5. Botdagi `/holat` buyrug'i — barcha mashinalar va servislar ro'yxati (🟢🟡🔴).

Necha kun oldin eslatishni o'zgartirish: `REMINDER_DAYS=7`.
Tekshirish vaqti: `application.properties` dagi `app.reminder-cron`.

## Admin panel: `/admin`

Uchta rol bor: **mashina egasi** (saytdagi sahifa), **avtoservis admini** va **super admin**.

### Super admin
Birinchi ishga tushishda avtomatik yaratiladi. Login: `SUPERADMIN_USERNAME` (standart `superadmin`),
parol: `SUPERADMIN_PASSWORD`. Parol berilmasa, tasodifiy parol yaratilib **konsolga bir marta**
chiqariladi. Kirgandan keyin "Parol" tugmasi orqali o'zgartiring.

Super admin:
- avtoservis qo'shadi (nomi, manzil, telefon) va uning **direktori** uchun login yaratadi.
  Qolgan xodimlarni direktor o'zi qo'shadi;
- **Faollik jurnali** — barcha servislarda kim, qachon, nima qilgani (servis bo'yicha filtr bilan);
- har bir servis holatini ko'radi. Muammolilar ro'yxat boshida turadi:
  - **Faol** — oxirgi 14 kun ichida ish kiritgan;
  - **Faol emas** — 14 kundan beri hech narsa kiritmagan;
  - **Ishlatmayapti** — qo'shilgandan beri bitta ham ish kiritmagan;
- har bir servis mijozlarini kuzatadi: vaqti kelgan → **qaytib kelgan / boshqa joyga ketgan / kelmagan**;
- **"Kelmaganlar va sababi"** tugmasi har bir mijoz uchun sababni ko'rsatadi:
  - *Boshqa servisga borgan* — keyingi ishni boshqa servis kiritgan;
  - *Telegram ulanmagan* — eslatma bormagan, qo'ng'iroq qilish kerak;
  - *Eslatma borgan, lekin kelmagan*;
  - *Eslatma yetkazilmagan* — mijoz botni bloklagan bo'lishi mumkin;
- servisni o'chiradi/yoqadi (o'chirilgan servis darhol tizimdan chiqariladi), parolini tiklaydi.

### Avtoservis xodimlari: rollar

| Imkoniyat | Direktor | Administrator | Usta |
|---|---|---|---|
| Ish kiritish | ✓ (ustani tanlaydi) | ✓ (ustani tanlaydi) | ✓ (o'zi nomidan) |
| Mijoz telefoni, «Tayyor, keling», qo'ng'iroq ro'yxati | ✓ | ✓ | — |
| Statistika | ✓ | ✓ | — |
| Xodim qo'shish, bloklash, parolini tiklash | ✓ | — | — |
| Servis faollik jurnali | ✓ | — | — |

Usta panelga kirganda faqat ish kiritish formasi va «Mening ishlarim» ro'yxatini ko'radi.
Bloklangan xodim darhol barcha qurilmalardan chiqariladi. Oldingi versiyadagi servis
loginlari dastur ishga tushganda avtomatik **direktor**ga aylantiriladi.

**Faollik jurnaliga** yoziladi: tizimga kirish, ish kiritish (qaysi usta qilgani bilan),
«Tayyor, keling», yangi mijoz, xodim qo'shish va bloklash, parol tiklash, servisni o'chirish,
mijozning ro'yxatdan o'tishi va botga ulanishi, kunlik eslatmalar natijasi.

### Ish kiritish jarayoni
- **Bajarilgan ishni kiritadi:** mashina raqamini yozadi → tizim egasini o'zi topadi. Mashina
  ro'yxatda bo'lmasa, egasining ism va telefonini kiritadi, mijoz avtomatik yaratiladi.
  Keyin nima qilingani (masalan **Ballon**), sana va keyingi muddat kiritiladi.
  Ish mijoz sahifasida darhol paydo bo'ladi, eslatma ham shunga qarab ishlaydi.
- **"Tayyor, keling"** — mijozning Telegramiga xabar boradi: mashina tayyor, servis nomi, manzili, telefoni.
  Telegram ulanmagan bo'lsa, mijozning telefoni ko'rsatiladi. Telefonda bosilsa, qo'ng'iroq boshlanadi.
- **Qo'ng'iroq ro'yxati** — muddati 7 kun ichida keladigan va o'tib ketgan mijozlar, telefonlari bilan.
- Shu servis kiritgan ishlar bo'yicha eslatmalarda servis nomi va telefoni ham ko'rsatiladi.

## Spamdan himoya

| Qayerda | Cheklov | Oshib ketsa |
|---|---|---|
| Bot, har bir foydalanuvchi | 1 daqiqada 15 xabar | Ortiqchasi javobsiz tashlanadi, 1 marta ogohlantiriladi |
| Bot, guruh/kanallar | — | E'tiborsiz qoldiriladi |
| Botdan yuborish (umumiy) | ~25 xabar/sekund | Navbatda kutadi; Telegram 429 bersa, aytgan vaqtcha kutib qayta yuboradi |
| Sayt API, har bir IP | 1 daqiqada 60 so'rov | 429 xato |
| Ro'yxatdan o'tish, har bir IP | 10 daqiqada 10 ta | 429 xato |
| "Sinov xabari", har bir IP | 10 daqiqada 3 ta | 429 xato |
| Admin login, har bir IP | 10 daqiqada 10 urinish | 429 xato (parolni taxmin qilishdan himoya) |
| "Tayyor, keling", bitta ish | 2 daqiqada 1 marta | Xato xabari |

Limitlarni `RateLimitFilter` va `TelegramBotPoller` dagi `new RateLimiter(...)` qatorlarida o'zgartirasiz.
Server Nginx orqasida bo'lsa, `X-Forwarded-For` sarlavhasini uzatishni sozlang (IP to'g'ri aniqlanishi uchun).

## 4. SMS (Eskiz.uz) ulash — ixtiyoriy

Standart holatda SMS **o'chiq**: barcha eslatmalar bepul Telegram bot orqali boradi, saytda
SMS qatori ko'rinmaydi. Keyinchalik kerak bo'lsa:

1. https://eskiz.uz da ro'yxatdan o'ting, balansni to'ldiring.
2. **Muhim:** Eskiz SMS matnlarini oldindan moderatsiyadan o'tkazadi. Kabinetda quyidagi
   shablonlarni yuboring (matnlar `ReminderService` dagi `weekSms` / `dueSms` da):
   ```
   Eslatma: {plate} mashinangizda {servis} almashtirishga {N} kun qoldi ({sana}).
   Eslatma: {plate} mashinangizda {servis} bugun almashtirish kuni ({sana}).
   Sinov xabari: eslatmalar shu raqamga keladi.
   ```
3. O'zgaruvchilar:
   ```bash
   export SMS_ENABLED=true
   export ESKIZ_EMAIL="siz@mail.uz"
   export ESKIZ_PASSWORD="..."
   export ESKIZ_FROM="4546"     # Eskiz bergan nik (alpha-name)
   ```
Foydalanuvchi o'z sahifasida SMS ni yoqib/o'chira oladi. Boshqa provayder (Play Mobile,
GetSMS) kerak bo'lsa faqat `SmsService` ni almashtirasiz.

## 5. Serverga joylash (VPS)

```bash
mvn clean package -DskipTests        # target/carcare.jar hosil bo'ladi
```

Serverda (PostgreSQL bilan):
```bash
export DB_URL="jdbc:postgresql://localhost:5432/carcare"
export DB_USER="postgres"
export DB_PASSWORD="..."
export APP_BASE_URL="https://carcare.uz"     # xabarlardagi havolalar uchun
export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_BOT_USERNAME="CarCareUzBot"
java -jar carcare.jar
```
Yoki Docker: `docker build -t carcare . && docker run -p 8080:8080 --env-file .env carcare`

Jadvallar avtomatik yaratiladi (`ddl-auto=update`). Dasturni faqat **bitta nusxada** ishga
tushiring — ikkita nusxa bir botdan xabar olishga urinib, bir-biriga xalaqit beradi.

## API

| Metod | Manzil | Vazifasi |
|---|---|---|
| GET | `/api/service-types` | Servis turlari ro'yxati |
| POST | `/api/cars` | Ro'yxatdan o'tish / qayta kirish |
| GET | `/api/cars/{token}` | Mashina va servislar |
| PATCH | `/api/cars/{token}/settings` | `{ "smsEnabled": true }` |
| POST | `/api/cars/{token}/records` | Servis qo'shish |
| DELETE | `/api/cars/{token}/records/{id}` | Servis o'chirish |
| POST | `/api/cars/{token}/test` | Sinov xabari |

## Keyingi qadamlar (g'oyalar)

- Saytga kirishni ham Telegram orqali tasdiqlash (hozir sahifa maxfiy havola orqali ochiladi)
- Admin panel: barcha mashinalar, statistika, ommaviy xabar
- Botning o'zidan servis qo'shish ("Moy almashtirdim" tugmasi)
- Servis turlari uchun standart muddatlar (moy — 6 oy / 10 000 km)
