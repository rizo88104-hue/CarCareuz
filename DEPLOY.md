# CarCare'ni serverga chiqarish

Bu yo'riqnoma loyihani haqiqiy serverga chiqaradi. Natijada:
- PostgreSQL baza;
- `https://domeningiz` manzilida ishlaydigan sayt;
- haqiqiy Telegram bot;
- har kuni olinadigan zaxira nusxa.

Demo ma'lumotlar yo'q: baza bo'sh boshlanadi, servislarni super admin o'zi qo'shadi.

## 1. Kerakli narsalar

| Nima | Tavsiya |
|---|---|
| Server (VPS) | Ubuntu 22.04 yoki 24.04, kamida 2 GB RAM, 1–2 CPU, 20 GB disk |
| Domen | Masalan `carcare.uz`. A-yozuvi server IP manziliga yo'naltirilgan bo'lishi kerak |
| Telegram bot | @BotFather → `/newbot` → token va bot nomi |

O'zbekistonda server qiyin bo'lsa, istalgan VPS provayderi (Hetzner, DigitalOcean, Contabo va mahalliy hostinglar) mos keladi.
Shaxsiy ma'lumotlarni O'zbekiston hududida saqlash talab qilinsa, mahalliy hostingni tanlang.

## 2. Serverni tayyorlash

Serverga SSH orqali kiring va quyidagilarni bajaring:

```bash
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh
apt install -y nginx certbot python3-certbot-nginx
ufw allow OpenSSH && ufw allow 'Nginx Full' && ufw --force enable
```

## 3. Loyihani yuklash

Kompyuteringizdan loyiha papkasini serverga ko'chiring (Windows'da WinSCP yoki `scp` bilan):

```bash
scp -r carcare root@SERVER_IP:/opt/carcare
```

Agar loyiha GitHub'da bo'lsa: `git clone ... /opt/carcare`.

## 4. Sozlamalar (.env)

```bash
cd /opt/carcare
cp .env.example .env
nano .env
```

Quyidagilarni albatta to'ldiring:

| O'zgaruvchi | Nima yoziladi |
|---|---|
| `APP_BASE_URL` | `https://carcare.uz` (o'z domeningiz) |
| `DB_PASSWORD` | Uzun tasodifiy parol: `openssl rand -base64 24` |
| `SUPERADMIN_PASSWORD` | Sizning parolingiz: kamida 12 belgi, harf va raqam |
| `TELEGRAM_BOT_TOKEN` | @BotFather bergan token |
| `TELEGRAM_BOT_USERNAME` | Bot nomi, @ belgisisiz |

`.env` faylini hech kimga yubormang.

## 5. Ishga tushirish

```bash
docker compose up -d --build
docker compose logs -f app
```

Birinchi yig'ish 3–5 daqiqa davom etadi. Logda `Started CarCareApplication` chiqsa, ilova ishga tushgan bo'ladi.
Logdan chiqish uchun Ctrl + C bosing — ilova to'xtamaydi.

## 6. Domen va HTTPS

```bash
cp deploy/nginx-carcare.conf /etc/nginx/sites-available/carcare
nano /etc/nginx/sites-available/carcare      # carcare.uz ni o'z domeningizga almashtiring
ln -s /etc/nginx/sites-available/carcare /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
certbot --nginx -d carcare.uz -d www.carcare.uz
```

Certbot HTTPS'ni o'zi sozlaydi va sertifikatni avtomatik yangilab turadi.

## 7. Birinchi kirish

1. `https://carcare.uz/admin` ni oching.
2. `superadmin` va `.env` dagi parolingiz bilan kiring.
3. «+ Servis qo'shish» → servis nomi, telefoni, manzili, direktor ismi, logini va paroli.
4. Direktorga **faqat uning login va parolini** bering. Super admin sizda qoladi.
5. Direktor o'zi xodim qo'shadi va kerak bo'lsa 🎁 Sadoqat dasturini yoqadi.
6. Tekshirish: `https://carcare.uz` da o'zingizni mijoz qilib ro'yxatdan o'tkazing, Telegram'ni ulang va «Sinov xabari» ni bosing.

## 8. Kunlik zaxira nusxa

```bash
crontab -e
```

Ochilgan faylga quyidagi qatorni qo'shing:

```
30 3 * * * /opt/carcare/deploy/backup.sh >> /var/log/carcare-backup.log 2>&1
```

Har kuni soat 03:30 da baza nusxasi `/var/backups/carcare` ga yoziladi va 14 kun saqlanadi.
Nusxalarni vaqti-vaqti bilan boshqa joyga ham ko'chirib turing (Google Drive, boshqa server) — server buzilsa ham ma'lumot qolsin.

Bazani nusxadan tiklash:

```bash
gunzip -c /var/backups/carcare/FAYL.sql.gz | docker compose exec -T db psql -U carcare -d carcare
```

## 9. Kundalik ishlar

| Vazifa | Buyruq |
|---|---|
| Loglarni ko'rish | `docker compose logs -f app` |
| Qayta ishga tushirish | `docker compose restart app` |
| Yangi versiyani o'rnatish | Yangi fayllarni `/opt/carcare` ga ko'chiring → `docker compose up -d --build` |
| To'xtatish | `docker compose down` (ma'lumotlar o'chmaydi) |
| Holat | `docker compose ps` |

Yangi versiyada qo'shilgan jadval va ustunlar ishga tushganda avtomatik qo'shiladi.
Shunga qaramay, har yangilashdan oldin `deploy/backup.sh` ni qo'lda ishga tushirib qo'ying.

## 10. Muhim eslatmalar

- **Bitta bot — bitta server.** Bitta Telegram tokeni bilan ikki joyda ishga tushirilsa (masalan serverda va kompyuteringizda), xabarlar ikkiga bo'linib ketadi. Kompyuterda sinash uchun alohida test bot yarating.
- **Super admin parolini** hech kimga bermang. Kirgandan keyin «Parol» tugmasi orqali almashtirsa bo'ladi.
- **SMS** o'chiq (`SMS_ENABLED=false`). Yoqilsa, Eskiz.uz har bir SMS uchun pul oladi.
- **Server quvvati.** 2 GB RAM o'nlab servis va bir necha ming mijozga yetadi. Sekinlashsa, serverni kattalashtiring va `JAVA_TOOL_OPTIONS` dagi `-Xmx` ni oshiring.

## 11. Topshirishdan oldin tekshiruv ro'yxati

- [ ] Domen HTTPS bilan ochiladi, brauzerda qulf belgisi bor
- [ ] Super admin paroli kuchli, `.env` faqat serverda
- [ ] Test mijoz ro'yxatdan o'tdi, Telegram ulandi, «Sinov xabari» keldi
- [ ] Test servis qo'shildi, direktor kira oldi, ish kiritildi va mijoz sahifasida ko'rindi
- [ ] Zaxira nusxa cron'ga qo'shildi va `deploy/backup.sh` qo'lda bir marta muvaffaqiyatli ishladi
- [ ] Test ma'lumotlar o'chirildi (yoki baza toza holatda boshlandi)
