/*
 * CarCare — sahifalar tili (o'zbek / rus).
 *
 * Qanday ishlaydi: sahifa matnlari HTML va JS da o'zbekcha yozilgan (asl til). Rus tili tanlanganda
 * DOM dagi matn tugunlari va placeholder/title/aria-label atributlari RU lug'ati bo'yicha almashtiriladi,
 * MutationObserver keyin qo'shilgan (innerHTML/textContent) matnlarni ham tarjima qiladi.
 *
 * Lug'at kaliti — o'zbekcha matn (bo'sh joylar bir xillashtirilgan). {0}, {1}… — o'zgaruvchan qism
 * (raqam, ism, sana): qolip ko'rinishidagi kalit regex ga aylantiriladi.
 *
 * Global: LANG ('uz'|'ru'), T(matn) — JS dagi confirm/alert/title uchun, setLang('ru').
 * Serverga har so'rovda X-Lang sarlavhasi yuboriladi — xatolar va enum nomlari shu tilda keladi.
 */
(function () {
  const KEY = 'carcare:lang';
  let lang = 'uz';
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === 'ru' || saved === 'uz') lang = saved;
    else if ((navigator.language || '').toLowerCase().startsWith('ru')) lang = 'ru';
  } catch (e) {}
  window.LANG = lang;
  document.documentElement.lang = lang;

  // =====================================================================
  //  RU lug'ati: "o'zbekcha" : "русский"
  // =====================================================================
  const RU = {
    // ---------- umumiy ----------
    'Saqlash': 'Сохранить', 'Bekor qilish': 'Отмена', 'Yopish': 'Закрыть', 'Nusxalash': 'Копировать', 'Nusxalandi ✓': 'Скопировано ✓',
    'Qidirish': 'Поиск', 'Tahrirlash': 'Редактировать', '✏️ Tahrirlash': '✏️ Редактировать', 'Izoh': 'Комментарий', 'Telefon': 'Телефон',
    'Ism': 'Имя', 'Familiya': 'Фамилия', 'Ism familiya': 'Имя и фамилия', 'Mashina raqami': 'Номер машины', 'Mashina': 'Машина', 'Mijoz': 'Клиент',
    'Sana': 'Дата', 'Ish': 'Работа', 'Usta': 'Мастер', 'Probeg': 'Пробег', 'Narx': 'Цена', "To'langan": 'Оплачено', 'Keyingisi': 'Следующая',
    'Qoldiq': 'Остаток', 'Holati': 'Статус', 'Holat': 'Статус', 'Qarz': 'Долг', 'Jami': 'Итого', 'Summa': 'Сумма', 'Turi': 'Тип', 'Ball': 'Баллы',
    'Balans': 'Баланс', 'Kim': 'Кто', 'Daraja': 'Уровень', 'Karta': 'Карта', 'Rol': 'Роль', 'Login': 'Логин', 'Parol': 'Пароль',
    'Yuklanmoqda…': 'Загрузка…', 'Yuklanmoqda...': 'Загрузка...', 'ixtiyoriy': 'необязательно', 'Hech narsa topilmadi': 'Ничего не найдено',
    'Hammasi': 'Все', 'Barchasi': 'Все', 'Faol': 'Активный', 'Yangi': 'Новый', 'Bugun': 'Сегодня', 'Bugun!': 'Сегодня!',
    "Telegram yo'q": 'Нет Telegram', 'Telegram ✓': 'Telegram ✓', 'Erkak': 'Мужской', 'Ayol': 'Женский', '—': '—',
    'Server bilan aloqa yo\'q. Internetni tekshirib, birozdan keyin qayta urinib ko\'ring.': 'Нет связи с сервером. Проверьте интернет и попробуйте чуть позже.',
    'Server javob bermadi (kod {0}). Birozdan keyin qayta urinib ko\'ring.': 'Сервер не ответил (код {0}). Попробуйте чуть позже.',
    '{0} kun qoldi': 'осталось {0} дн.', "{0} kun o'tdi": 'просрочено на {0} дн.', '{0} kun': '{0} дн.', '{0} ta': '{0}', '{0} so\'m': '{0} сум', '{0} ball': '{0} баллов',
    '+{0} ball': '+{0} баллов', '{0} ta ish': '{0} работ', '{0} gacha': 'до {0}', 'mijoz': 'клиентов',
    'CarCare · Admin': 'CarCare · Админ', 'Super admin': 'Супер-админ',

    // ---------- index.html ----------
    'Moy almashtirish vaqtini unutmang': 'Не забывайте вовремя менять масло',
    'Ma\'lumotlaringizni kiriting — mashinangiz uchun shaxsiy sahifa ochiladi. Muddatga 1 hafta qolganda Telegram orqali eslatamiz.':
      'Введите данные — для вашей машины откроется личная страница. За неделю до срока напомним в Telegram.',
    'Telefon raqam': 'Номер телефона',
    'Oldin ro\'yxatdan o\'tgan bo\'lsangiz, xuddi shu raqam va telefon bilan sahifangizga qayta kirasiz. Telegram ulangan bo\'lsa, kirishni botda «Ha, bu men» tugmasi bilan tasdiqlaysiz.':
      'Если вы уже регистрировались, войдите с тем же номером машины и телефоном. Если подключён Telegram, подтвердите вход в боте кнопкой «Да, это я».',
    'Davom etish →': 'Продолжить →', 'Yuborilmoqda...': 'Отправка...',
    "Iltimos, barcha maydonlarni to'ldiring": 'Пожалуйста, заполните все поля',
    '🔐 Telegram\'da tasdiqlang': '🔐 Подтвердите в Telegram',
    'Bu mashina Telegram botga ulangan. Egasiga «Bu sizmisiz?» degan xabar yubordik. Botda': 'Эта машина подключена к Telegram-боту. Владельцу отправлено сообщение «Это вы?». В боте нажмите',
    '«✅ Ha, bu men»': '«✅ Да, это я»', 'tugmasini bosing — sahifa shu yerda o\'zi ochiladi.': '— страница откроется здесь сама.',
    'Telegram botni ochish': 'Открыть Telegram-бота', 'Kutilmoqda… So\'rov 5 daqiqa amal qiladi.': 'Ожидание… Запрос действует 5 минут.',
    'Oxirgi kirgan mashinangiz': 'Последняя открытая машина', '🚗 {0} sahifasini ochish': '🚗 Открыть страницу {0}',
    'Ma\'lumotlaringiz faqat eslatma yuborish uchun ishlatiladi.': 'Ваши данные используются только для напоминаний.',
    'Foto:': 'Фото:',
    'So\'rov topilmadi. Qayta urinib ko\'ring.': 'Запрос не найден. Попробуйте снова.',
    'Kirish Telegram\'da rad etildi. Agar bu siz bo\'lsangiz, qayta urinib, botda «Ha, bu men» ni bosing.': 'Вход отклонён в Telegram. Если это вы, попробуйте снова и нажмите «Да, это я» в боте.',
    'Tasdiqlash vaqti tugadi. Qayta urinib ko\'ring.': 'Время подтверждения истекло. Попробуйте снова.',

    // ---------- car.html ----------
    'Mashina topilmadi': 'Машина не найдена', 'Havola noto\'g\'ri bo\'lishi mumkin.': 'Возможно, ссылка неверна.', 'Bosh sahifaga': 'На главную',
    '🛣 Probeg': '🛣 Пробег', 'Hozirgi probeg, km': 'Текущий пробег, км',
    '{0} km · {1}. Probeg bo\'yicha servisga {2} km qolganda eslatamiz.': '{0} км · {1}. Напомним, когда до обслуживания по пробегу останется {2} км.',
    '{0} km. Probeg bo\'yicha servisga {1} km qolganda eslatamiz.': '{0} км. Напомним, когда до обслуживания по пробегу останется {1} км.',
    'Kiritilmagan. Hozirgi probegni yozing — keyingi probeg yaqinlashganda eslatamiz. Botga shunchaki raqam yozib ham yangilash mumkin.':
      'Не указан. Введите текущий пробег — напомним, когда приблизится следующий. Можно просто написать число боту.',
    'Eslatmalar': 'Напоминания', 'Ulangan': 'Подключён',
    'Ulanmagan — tugmani bosing, botda START va "Raqamni yuborish" ni bosing': 'Не подключён — нажмите кнопку, в боте нажмите START и «Отправить номер»',
    'Botni ochish': 'Открыть бота', 'Telegramga ulash': 'Подключить Telegram', 'Bot hali sozlanmagan (administrator)': 'Бот ещё не настроен (администратор)',
    'Hozircha mavjud emas': 'Пока недоступно', '{0} raqamiga keladi': 'на номер {0}', "O'chirilgan": 'Отключено',
    'Muddatga {0} kun qolganda va muddat kuni eslatamiz.': 'Напомним за {0} дн. до срока и в день срока.',
    'Sinov xabari': 'Тестовое сообщение', 'Sinov xabari yuborildi ✅': 'Тестовое сообщение отправлено ✅',
    '🔐 Xavfsizlik': '🔐 Безопасность', 'Qayta kirish': 'Повторный вход', 'Sahifa havolasi': 'Ссылка на страницу',
    'Telefon + mashina raqami bilan qayta kirish faqat siz Telegram botda «Ha, bu men» ni bosganingizda ishlaydi ✅': 'Повторный вход по телефону и номеру машины работает только после вашего подтверждения «Да, это я» в Telegram-боте ✅',
    'Hozir telefon + mashina raqamini bilgan har kim sahifaga kira oladi. Telegramga ulansangiz, qayta kirish faqat sizning tasdig\'ingiz bilan bo\'ladi.': 'Сейчас на страницу может войти любой, кто знает телефон и номер машины. Подключите Telegram — тогда вход будет только с вашего подтверждения.',
    'Havola begonaga tushgan deb o\'ylasangiz, uni yangilang — eski havola darhol ishlamay qoladi.': 'Если ссылка попала к посторонним, обновите её — старая сразу перестанет работать.',
    '🔄 Havolani yangilash': '🔄 Обновить ссылку',
    'Havola yangilansinmi?\n\nEski havola darhol ishlamay qoladi. Saqlab qo\'ygan havolangiz bo\'lsa, yangisini qayta saqlashingiz kerak.': 'Обновить ссылку?\n\nСтарая ссылка сразу перестанет работать. Если вы сохраняли ссылку, сохраните новую.',
    'Havola yangilandi ✅ Eski havola endi ishlamaydi. Bu sahifani qayta saqlab qo\'ying — yangi havola Telegramga ham yuborildi.': 'Ссылка обновлена ✅ Старая больше не работает. Сохраните эту страницу заново — новая ссылка отправлена и в Telegram.',
    'Havola yangilandi ✅ Eski havola endi ishlamaydi. Bu sahifani qayta saqlab qo\'ying.': 'Ссылка обновлена ✅ Старая больше не работает. Сохраните эту страницу заново.',
    'Servislar': 'Обслуживание', "+ Qo'shish": '+ Добавить', 'Nima almashtirildi?': 'Что заменили?', 'Nomi': 'Название',
    'Masalan: Rul tyagasi': 'Например: рулевая тяга', 'Almashtirilgan sana': 'Дата замены', 'Keyingi almashtirish': 'Следующая замена',
    '+1 oy': '+1 мес', '+3 oy': '+3 мес', '+6 oy': '+6 мес', '+1 yil': '+1 год', 'Keyingi probeg, km': 'Следующий пробег, км',
    'Masalan: Mobil 5W-30, 4 litr': 'Например: Mobil 5W-30, 4 литра',
    'Hali servis qo\'shilmagan.': 'Обслуживание ещё не добавлено.', 'Masalan, oxirgi marta moy almashtirgan sanangizni kiriting.': 'Например, укажите дату последней замены масла.',
    "Tarix bo'sh": 'История пуста', 'Tarix': 'История',
    '🎁 Jamg\'arma kartalarim': '🎁 Мои накопительные карты',
    '1 ball = 1 so\'m. Ballarni shu servisda to\'lovda ishlatish mumkin — kassada karta raqamini yoki mashina raqamini ayting.': '1 балл = 1 сум. Баллами можно оплачивать в этом сервисе — назовите на кассе номер карты или машины.',
    'Keshbek {0}% · chegirma {1}% · {2} darajagacha {3} so\'m': 'Кешбэк {0}% · скидка {1}% · до уровня {2}: {3} сум',
    'Keshbek {0}% · {1} darajagacha {2} so\'m': 'Кешбэк {0}% · до уровня {1}: {2} сум',
    'Keshbek {0}% · chegirma {1}%': 'Кешбэк {0}% · скидка {1}%', 'Keshbek {0}%': 'Кешбэк {0}%',
    'Ballar {0} gacha amal qiladi': 'Баллы действуют до {0}', 'Dastur hozir to\'xtatilgan, ballaringiz saqlangan': 'Программа приостановлена, ваши баллы сохранены',
    'Probeg yetdi!': 'Пробег достигнут!', "{0} km o'tdi": 'превышен на {0} км', '{0} km qoldi': 'осталось {0} км',
    'Almashtirildi: {0}': 'Заменено: {0}', 'Almashtirildi: {0} · {1} km': 'Заменено: {0} · {1} км',
    'Keyingisi: {0} km da': 'Следующая: на {0} км', 'Keyingisi: {0} km da ({1} km qoldi)': 'Следующая: на {0} км (осталось {1} км)', 'Keyingisi: {0} km da (probeg yetdi)': 'Следующая: на {0} км (пробег достигнут)',
    'Keyingi:': 'Следующая:', 'Almashtirdim': 'Заменил(а)', "O'chirish": 'Удалить', "Bu yozuvni o'chirasizmi?": 'Удалить эту запись?',
    'Ikkala sanani ham kiriting': 'Введите обе даты', 'Saqlandi ✅': 'Сохранено ✅', "O'chirildi": 'Удалено',
    'Probegni kiriting (km)': 'Введите пробег (км)', 'Probeg saqlandi ✅': 'Пробег сохранён ✅',

    // ---------- admin: kirish, yuqori qism ----------
    'Tizimga kirish': 'Вход в систему', 'Kirish': 'Войти',
    '🔐 Telegram botga': '🔐 В Telegram-бот отправлен', '6 xonali kod': '6-значный код', 'yuborildi. Kod 5 daqiqa amal qiladi.': '. Код действует 5 минут.',
    'Tasdiqlash kodi': 'Код подтверждения', 'Tasdiqlash': 'Подтвердить', '← Orqaga': '← Назад', 'Chiqish': 'Выйти',
    "Parolni o'zgartirish": 'Смена пароля', 'Joriy parol': 'Текущий пароль', 'Yangi parol (kamida 8 belgi)': 'Новый пароль (минимум 8 символов)', "Parol o'zgartirildi": 'Пароль изменён',
    'Avtoservislar': 'Автосервисы', 'Mijozlar': 'Клиенты', 'Faollik jurnali': 'Журнал действий', 'Bosh sahifa': 'Главная', 'Ishlar': 'Работы',
    'Mijozlar bazasi': 'База клиентов', 'Qarz daftari': 'Книга долгов', '🎁 Sadoqat': '🎁 Лояльность', 'Xodimlar': 'Сотрудники', 'Ish kiritish': 'Внести работу',

    // ---------- admin: ishlar ----------
    'Bajarilgan ishni kiritish': 'Внести выполненную работу',
    'Bu mashina hali ro\'yxatda yo\'q — egasining ma\'lumotlarini kiriting.': 'Этой машины ещё нет в базе — введите данные владельца.',
    'Nima qilindi?': 'Что сделано?', '— bir nechtasini tanlash mumkin': '— можно выбрать несколько', 'Bajarilgan sana': 'Дата выполнения',
    'Probeg, km': 'Пробег, км', 'oxirgi: {0} km': 'последний: {0} км', 'Masalan: 205/55 R16': 'Например: 205/55 R16',
    'Jami narx, so\'m': 'Итого, сум', 'Jami narx (chegirmagacha), so\'m': 'Итого (до скидки), сум', 'ishlar narxidan': 'из цен работ',
    'Hozir to\'landi, so\'m': 'Оплачено сейчас, сум', "to'liq to'landi": 'оплачено полностью', 'Chegirmani qo\'llash': 'Применить скидку',
    'Sadoqat chegirmasi {0}% ni qo\'llash': 'Применить скидку лояльности {0}%', 'Ball bilan to\'lash, so\'m': 'Оплата баллами, сум', 'Maksimal': 'Максимум',
    "To'lov turi": 'Способ оплаты', 'Naqd': 'Наличные', "O'tkazma": 'Перевод', '🎁 Bonus ball': '🎁 Бонусные баллы', 'Qarzni to\'lash muddati': 'Срок оплаты долга',
    'Mijozga "Tayyor, keling" xabarini yuborish': 'Отправить клиенту «Готово, забирайте»', 'Ish saqlanadi, mijozga xabarni administrator yuboradi.': 'Работа сохранится, сообщение клиенту отправит администратор.',
    "Qo'ng'iroq ro'yxati": 'Список для звонков', '· muddati yaqin va o\'tganlar': '· скоро срок и просроченные', 'Oxirgi ishlar': 'Последние работы', 'Mening ishlarim': 'Мои работы',
    'Nima qilindi (nomi)': 'Что сделано (название)', 'Masalan: Generator remonti': 'Например: ремонт генератора', '{0} ni olib tashlash': 'Убрать {0}',
    'Yuqoridan bajarilgan ishni tanlang.': 'Выберите выполненную работу выше.', 'masalan 250 000': 'например 250 000', 'Narxi, so\'m': 'Цена, сум',
    'Avval hozirgi probegni («Probeg, km») kiriting.': 'Сначала введите текущий пробег («Пробег, км»).',
    'Nima qilinganini tanlang — kamida bitta ish.': 'Выберите, что сделано — минимум одну работу.',
    'Hozirgi servislar: {0}': 'Текущие работы: {0}', "⛔ Qora ro'yxatda:": '⛔ В чёрном списке:', "Qora ro'yxatda": 'В чёрном списке',
    'Oxirgi 30 kunda ish': 'Работ за 30 дней', 'Qaytib kelganlar': 'Вернувшиеся', 'Telegram ulangan': 'Подключён Telegram',
    "Hozircha qo'ng'iroq qilinadigan mijoz yo'q": 'Пока некому звонить', 'Hali ish kiritilmagan': 'Работ ещё нет', 'Yangilangan': 'Обновлено',
    'Narx kiritilmagan': 'Цена не указана', "Narx qo'yish": 'Указать цену', 'Narx:': 'Цена:', "💵 To'lov": '💵 Оплата', "💵 To'lash": '💵 Оплатить', "To'lash": 'Оплатить',
    '{0} · bajarildi {1} · keyingisi {2} · usta: {3} · {4}': '{0} · выполнено {1} · следующая {2} · мастер: {3} · {4}',
    '{0} · bajarildi {1} · keyingisi {2} · usta: {3}': '{0} · выполнено {1} · следующая {2} · мастер: {3}',
    '{0} · bajarildi {1} · keyingisi {2} · {3}': '{0} · выполнено {1} · следующая {2} · {3}',
    '{0} · bajarildi {1} · keyingisi {2}': '{0} · выполнено {1} · следующая {2}',
    'bajarildi {0} · keyingisi {1} · usta: {2} · {3}': 'выполнено {0} · следующая {1} · мастер: {2} · {3}',
    'bajarildi {0} · keyingisi {1} · usta: {2}': 'выполнено {0} · следующая {1} · мастер: {2}',
    'bajarildi {0} · keyingisi {1} · {2}': 'выполнено {0} · следующая {1} · {2}',
    'bajarildi {0} · keyingisi {1}': 'выполнено {0} · следующая {1}',
    'qarz {0} so\'m': 'долг {0} сум', '(muddat {0})': '(срок {0})', "· to'liq to'langan": '· оплачено полностью',
    '✅ Tayyor, keling': '✅ Готово, забирайте', '📲 QR — Telegramga ulash': '📲 QR — подключить Telegram', '🖨 Chek': '🖨 Чек',
    'Xabar yuborilgan: {0}': 'Сообщение отправлено: {0}', 'Yuborildi ✓ ({0})': 'Отправлено ✓ ({0})', "Yetkazilmadi — QR ko'rsating / qo'ng'iroq qiling": 'Не доставлено — покажите QR / позвоните',
    '{0} (o\'zim)': '{0} (я)',
    '🖨 Chek chiqarish (QR bilan)': '🖨 Напечатать чек (с QR)', 'Ulash havolasini nusxalash': 'Копировать ссылку подключения', 'Mijoz sahifasi havolasi': 'Ссылка на страницу клиента',
    'Mijoz sahifasi:': 'Страница клиента:', 'Havolani nusxalash': 'Копировать ссылку',

    // ---------- admin: QR va chek ----------
    'Telegram botga ulash QR kodi': 'QR-код подключения к Telegram-боту', '📲 Mijoz telefon kamerasi bilan skaner qilsin': '📲 Клиент сканирует камерой телефона',
    'Bot ochiladi →': 'Откроется бот →', 'START': 'START', '«📱 Raqamni yuborish»': '«📱 Отправить номер»',
    '. Shundan keyin servis eslatmalari va «Tayyor» xabari Telegramga bepul keladi.': '. После этого напоминания и сообщение «Готово» будут приходить в Telegram бесплатно.',
    'Bu mijoz Telegramga ulangan ✓': 'Этот клиент подключён к Telegram ✓', 'Telegramga ulash': 'Подключить Telegram',
    'usta: {0}': 'мастер: {0}', '↳ keyingisi: {0}': '↳ следующая: {0}', '↳ keyingisi: {0} · probeg {1} km': '↳ следующая: {0} · пробег {1} км',
    'Eslatmalar Telegram botga keladi ✓': 'Напоминания приходят в Telegram-бот ✓', 'Servis vaqtini eslatib turamiz!': 'Напомним о сроках обслуживания!',
    'QR ni telefon kamerasi bilan skaner qiling → Telegram botda START → «Raqamni yuborish». Moy, filtr va boshqa servislar vaqti kelganda bepul eslatma keladi.':
      'Отсканируйте QR камерой телефона → в Telegram-боте START → «Отправить номер». Когда придёт срок замены масла, фильтров и т.д., придёт бесплатное напоминание.',
    'Mashina sahifasi: {0}': 'Страница машины: {0}', 'Rahmat! 🙏': 'Спасибо! 🙏',

    // ---------- admin: xodimlar ----------
    'Bloklangan xodim barcha qurilmalardan darhol chiqariladi.': 'Заблокированный сотрудник сразу выходит на всех устройствах.',
    'Yangi xodim': 'Новый сотрудник', 'Usta — faqat ish kiritadi': 'Мастер — только вносит работы', 'Administrator — mijozlar bilan ishlaydi': 'Администратор — работает с клиентами',
    'kamida 8 belgi': 'минимум 8 символов', "Qo'shish": 'Добавить', 'Bloklangan': 'Заблокирован', 'Direktor': 'Директор', 'Faollashtirish': 'Активировать', 'Bloklash': 'Заблокировать',
    'Yangi parol, kamida 8 belgi': 'Новый пароль, минимум 8 символов', "Xodim qo'shildi. Login va parolni unga bering.": 'Сотрудник добавлен. Передайте ему логин и пароль.',
    'Parol yangilandi. Xodim qaytadan kirishi kerak.': 'Пароль обновлён. Сотруднику нужно войти заново.',

    // ---------- admin: bosh sahifa ----------
    'Tushum dinamikasi': 'Динамика выручки', 'Qarzdorlar': 'Должники', 'Qarz daftari →': 'Книга долгов →', "🎂 Shu oy tug'ilgan kunlar": '🎂 Дни рождения в этом месяце',
    'Bugungi ishlar': 'Работ сегодня', 'Shu oy: {0} ta': 'За месяц: {0}', 'Bugungi tushum': 'Выручка сегодня', 'Shu oy: {0} so\'m': 'За месяц: {0} сум',
    "Qarzlar qoldig'i": 'Остаток долгов', "{0} qarzdor, {1} tasi muddati o'tgan": '{0} должников, {1} просрочено', '7 kun ichida keladi': 'Приедут за 7 дней', "Qo'ng'iroq ro'yxatida": 'В списке для звонков',
    'Barcha mijozlar': 'Все клиенты', 'Shu oy yangi mijozlar': 'Новых клиентов за месяц', "Shu oy tug'ilganlar": 'Именинники месяца', '{0} ta · {1} so\'m': '{0} · {1} сум',
    'muddatida': 'в срок', "Qarzdor yo'q 🎉": 'Должников нет 🎉', '✉️ Tabriklash': '✉️ Поздравить',
    'Bot tug\'ilgan kuni ertalab 09:00 da avtomatik tabrik yuboradi. Chegirma taklif qilmoqchi bo\'lsangiz, «Tabriklash» orqali yozing.': 'Бот автоматически поздравит в день рождения в 09:00. Хотите предложить скидку — напишите через «Поздравить».',
    "Shu oy tug'ilgan kun yo'q. Profilda tug'ilgan kunni kiriting.": 'В этом месяце дней рождения нет. Укажите дату рождения в профиле.',
    "Shu oy hali to'lov qabul qilinmagan": 'В этом месяце оплат ещё не было', '{0} mln': '{0} млн', '{0} ming': '{0} тыс.',
    'Shu oy kunlik tushum, jami {0} so\'m': 'Выручка по дням за месяц, всего {0} сум', 'Jami:': 'Всего:',
    "· ustun ustiga olib boring — kunlik summa. To'q sariq — bugun.": '· наведите на столбик — сумма за день. Оранжевый — сегодня.',
    '· yanvar': '· январь', '· fevral': '· февраль', '· mart': '· март', '· aprel': '· апрель', '· may': '· май', '· iyun': '· июнь',
    '· iyul': '· июль', '· avgust': '· август', '· sentabr': '· сентябрь', '· oktabr': '· октябрь', '· noyabr': '· ноябрь', '· dekabr': '· декабрь',

    // ---------- admin: qarz daftari ----------
    "Qarzga qilingan ishlar. Davr — ish bajarilgan sana bo'yicha.": 'Работы в долг. Период — по дате выполнения.',
    'Mijoz ismi, telefon yoki mashina raqami': 'Имя клиента, телефон или номер машины', 'Qarz summasi': 'Сумма долга', "To'lov muddati": 'Срок оплаты',
    'Joriy yil': 'Этот год', 'Joriy oy': 'Этот месяц', 'Joriy hafta': 'Эта неделя', "Muddati o'tgan": 'Просрочено', "To'lanmagan": 'Не оплачено', "Qisman to'langan": 'Частично оплачено',
    'Hammasi · {0}': 'Все · {0}', "Muddati o'tgan · {0} kun": 'Просрочено · {0} дн.', "Muddati o'tgan · {0}": 'Просрочено · {0}', "To'lanmagan · {0}": 'Не оплачено · {0}',
    "Qisman to'langan · {0}": 'Частично оплачено · {0}', "To'langan · {0}": 'Оплачено · {0}', 'Qisman · muddatida': 'Частично · в срок', 'Muddatida': 'В срок',
    'Qarzdorlar soni': 'Должников', 'Qarzlar summasi': 'Сумма долгов', "To'lovlar summasi": 'Сумма оплат', 'Qarz topilmadi': 'Долгов не найдено',
    "{0} tadan {1} tasi ko'rsatildi": 'Показано {1} из {0}', '{0} ta yozuv': 'Записей: {0}',
    "To'lov qabul qilish": 'Принять оплату', "To'lovlar tarixi": 'История оплат', "Summa, so'm": 'Сумма, сум', "Narx, so'm": 'Цена, сум', "Qarz qoldig'i:": 'Остаток долга:',
    'Kartada:': 'На карте:', '. Har bir ish narxining {0}% gacha ball bilan to\'lash mumkin. Balldan keshbek yozilmaydi.': '. Баллами можно оплатить до {0}% стоимости каждой работы. С баллов кешбэк не начисляется.',
    'Sadoqat chegirmasi {0}% ({1}) — narxdan ayiriladi': 'Скидка лояльности {0}% ({1}) — вычитается из цены', "Hali to'lov yo'q": 'Оплат ещё нет',

    // ---------- admin: pul yordamchilari / sadoqat ish formasida ----------
    '💳 Karta {0} · balans': '💳 Карта {0} · баланс', "Sadoqat dasturi to'xtatilgan — ball yozilmaydi va ishlatilmaydi.": 'Программа лояльности приостановлена — баллы не начисляются и не списываются.',
    '— ball yozilmaydi, chegirma berilmaydi.': '— баллы не начисляются, скидка не даётся.', 'Karta {0} · balans {1} ball': 'Карта {0} · баланс {1} баллов',
    "Jamg'arma karta ochiladi": 'Откроется накопительная карта', "· sovg'a": '· подарок',
    "Har to'lovdan {0}% ball qaytadi, chegirma {1}%": 'С каждой оплаты {0}% баллами, скидка {1}%', "Har to'lovdan {0}% ball qaytadi": 'С каждой оплаты {0}% баллами',
    '· karta {0} · balans': '· карта {0} · баланс',
    "Keshbek {0}% · chegirma {1}% · ball bilan narxning {2}% gacha to'lash mumkin": 'Кешбэк {0}% · скидка {1}% · баллами можно оплатить до {2}% цены',
    "Keshbek {0}% · ball bilan narxning {1}% gacha to'lash mumkin": 'Кешбэк {0}% · баллами можно оплатить до {1}% цены',
    "Mavjud: {0} ball · bu ishda ko'pi bilan": 'Доступно: {0} баллов · в этой работе не более', 'Mavjud: {0} ball': 'Доступно: {0} баллов', '(narxning {0}%)': '({0}% цены)',
    "Ball bilan {0} so'm dan ko'p to'lab bo'lmaydi": 'Баллами нельзя оплатить больше {0} сум',
    "Chegirma {0}%: −{1} so'm →": 'Скидка {0}%: −{1} сум →', "to'lanadi {0} so'm": 'к оплате {0} сум', "Ball bilan: {0} so'm, pul bilan: {1} so'm": 'Баллами: {0} сум, деньгами: {1} сум',
    "Narx kiritilmasa, ish narxsiz saqlanadi (keyin «Narx qo'yish» mumkin). Narxni har bir ish qatoriga yozing.": 'Если цену не указать, работа сохранится без цены (потом можно «Указать цену»). Цену пишите в строке каждой работы.',
    "Qarzga yoziladi: {0} so'm": 'В долг: {0} сум', "To'lov va ball birgalikda narxdan oshib ketdi": 'Оплата и баллы вместе превышают цену', "To'liq to'landi": 'Оплачено полностью',

    // ---------- admin: Telegram xabar ----------
    '✉️ Telegram xabar': '✉️ Сообщение в Telegram', 'Xabar matni': 'Текст сообщения',
    "Xabar boshida servis nomi, oxirida servis telefoni avtomatik qo'shiladi.": 'В начало автоматически добавится название сервиса, в конец — телефон.',
    'Yuborish': 'Отправить', 'Xabar yuborildi ✓': 'Сообщение отправлено ✓', 'Mashina tayyor': 'Машина готова', 'Aksiya': 'Акция', 'Tabrik + chegirma': 'Поздравление + скидка', 'Qarz haqida': 'О долге',
    'Telegramda xabar yozish': 'Написать в Telegram', '✉️ Xabar yozish': '✉️ Написать',

    // ---------- admin: mijozlar bazasi ----------
    "Excel'ga yuklab olish": 'Скачать в Excel', 'Mashina raqami, ism, telefon yoki izoh': 'Номер машины, имя, телефон или комментарий',
    "Yangi mijoz qo'shish": 'Добавить клиента', "Mashina tizimda bor bo'lsa, faqat sizning bazangizga bog'lanadi.": 'Если машина уже в системе, она просто привяжется к вашей базе.',
    "Tug'ilgan kun": 'Дата рождения', 'Jinsi': 'Пол', 'Mijoz qayerdan kelgan': 'Откуда пришёл клиент',
    'Egasi': 'Владелец', "Ro'yxatdan o'tgan": 'Зарегистрирован', 'Qaysi servislarda': 'В каких сервисах', 'Tashriflar': 'Визиты', 'Oxirgi tashrif': 'Последний визит', 'Keyingi servis': 'Следующее обслуживание', 'Amallar': 'Действия',
    'Butun tizimda {0} ta mashina, {1} tasi Telegramga ulangan.': 'Всего в системе {0} машин, {1} подключены к Telegram.', "faqat o'zi kiritgan": 'только сам владелец',
    "Oxirgi 200 tasi ko'rsatildi. Aniqroq qidiring.": 'Показаны последние 200. Уточните поиск.',
    'Servisingiz mijozlari. Qatorni bosing — mijoz profili ochiladi.': 'Клиенты вашего сервиса. Нажмите на строку — откроется профиль.',
    'Jami mijozlar': 'Всего клиентов', 'Faol mijozlar': 'Активные клиенты', 'Nofaol mijozlar': 'Неактивные клиенты', "🎂 Bu oy tug'ilgan kunlar": '🎂 Дни рождения в этом месяце',
    'Nofaol': 'Неактивные', 'Qayta kelgan': 'Вернувшиеся', '7 kun ichida': 'В течение 7 дней', 'Boshqa joyga ketgan': 'Ушли в другой сервис', 'Yangi (30 kun)': 'Новые (30 дней)', "Qora ro'yxat": 'Чёрный список',
    'Barchasi · {0}': 'Все · {0}', 'Faol · {0}': 'Активные · {0}', 'Nofaol · {0}': 'Неактивные · {0}', 'Qayta kelgan · {0}': 'Вернувшиеся · {0}', '7 kun ichida · {0}': 'В течение 7 дней · {0}',
    "Telegram yo'q · {0}": 'Нет Telegram · {0}', 'Boshqa joyga ketgan · {0}': 'Ушли в другой сервис · {0}', 'Yangi (30 kun) · {0}': 'Новые (30 дней) · {0}', "Qora ro'yxat · {0}": 'Чёрный список · {0}',
    "Jamg'arma karta: {0} ball": 'Накопительная карта: {0} баллов', "⛔ Qora ro'yxat": '⛔ Чёрный список', "Tug'ilgan kun: {0}": 'День рождения: {0}', 'Hali kelmagan': 'Ещё не приезжал',
    "{0} tadan {1} tasi ko'rsatildi. Qidiruv bilan toraytiring.": 'Показано {1} из {0}. Уточните поиск.', '{0} ta mijoz': 'Клиентов: {0}',

    // ---------- admin: mijoz profili ----------
    'Servislar tarixi': 'История обслуживания', "Qarzlar va to'lovlar": 'Долги и оплаты', '🎁 Bonus ballar': '🎁 Бонусные баллы',
    'Shu oyda': 'В этом месяце', 'Telegram bot': 'Telegram-бот', '● Ulangan': '● Подключён', '● Ulanmagan': '● Не подключён', 'Ulash': 'Подключить',
    'Mijoz telefonida QR ni skaner qilsin (yoki havolani yuboring). Botda START va «Raqamni yuborish» bosilsa, eslatmalar Telegramga keladi:': 'Пусть клиент отсканирует QR на телефоне (или отправьте ссылку). После START и «Отправить номер» в боте напоминания будут приходить в Telegram:',
    "📲 QR ko'rsatish": '📲 Показать QR', "Qora ro'yxatga qo'shish sababi": 'Причина добавления в чёрный список', "Masalan: qarzini to'lamadi, qo'pol muomala": 'Например: не оплатил долг, грубое поведение',
    "Qora ro'yxatdan chiqarish": 'Убрать из чёрного списка', "⛔ Qora ro'yxatga": '⛔ В чёрный список', "Qora ro'yxatga qo'shildi": 'Добавлен в чёрный список', "Qora ro'yxatdan chiqarildi": 'Убран из чёрного списка',
    'Umumiy qarz': 'Общий долг', 'Kechikish kunlari': 'Дней просрочки', "💵 Qarzni so'ndirish": '💵 Погасить долг', "💵 Qarzni so'ndirish · {0} so'm": '💵 Погасить долг · {0} сум',
    '📝 Izoh': '📝 Комментарий', 'Masalan: doimiy mijoz, 10% chegirma': 'Например: постоянный клиент, скидка 10%', "Izoh yo'q": 'Комментария нет',
    "Izohni faqat sizning servis xodimlari ko'radi.": 'Комментарий видят только сотрудники вашего сервиса.', 'Statistika': 'Статистика', 'Jami xarajat': 'Всего потрачено', "O'rtacha chek": 'Средний чек', 'Boshqa joyda': 'В другом месте',
    'Hozirgi servislar': 'Текущее обслуживание', '{0} · sizda': '{0} · у вас', '{0} · boshqa joyda': '{0} · в другом месте', "Faol servis yo'q": 'Активных работ нет',
    "Mijoz ma'lumotlarini tahrirlash": 'Редактирование данных клиента', "chegirma −{0} so'm": 'скидка −{0} сум', "shundan ball {0} so'm": 'из них баллами {0} сум',
    'Qarzga qilingan ishlar': 'Работы в долг', 'Ish sanasi': 'Дата работы', 'Muddat': 'Срок', "Qarz yo'q": 'Долгов нет', 'Tranzaksiyalar tarixi': 'История транзакций', 'Qabul qildi': 'Принял', "Ma'lumotlar mavjud emas": 'Данных нет',
    '{0} · barcha qarzlar': '{0} · все долги',
    'Keyingi darajagacha': 'До следующего уровня', '{0} darajagacha yana {1} so\'m': 'До уровня {0} ещё {1} сум', 'Eng yuqori daraja': 'Высший уровень',
    'Sadoqat dasturi yoqilmagan.': 'Программа лояльности не включена.', "Qora ro'yxatdagi mijozga karta ochilmaydi.": 'Клиенту из чёрного списка карта не открывается.',
    "Jamg'arma karta hali ochilmagan. Keyingi ishda avtomatik ochiladi.": 'Накопительная карта ещё не открыта. Откроется автоматически при следующей работе.', '💳 Karta ochish': '💳 Открыть карту',
    "🎁 Jamg'arma karta": '🎁 Накопительная карта', "Dastur to'xtatilgan": 'Программа приостановлена', 'Keshbek': 'Кешбэк', 'Chegirma': 'Скидка', "Jami yig'ilgan": 'Всего начислено', 'Ishlatilgan': 'Использовано',
    'Jami xarid (pul bilan)': 'Покупок (деньгами)', 'Ballar amal qiladi': 'Баллы действуют', 'muddatsiz': 'бессрочно', 'Amal': 'Действие', 'Ayirish': 'Списать', 'masalan 10 000': 'например 10 000', 'Sabab': 'Причина',
    'Masalan: shikoyat uchun kompensatsiya': 'Например: компенсация за жалобу', '± Ballarni tuzatish': '± Корректировать баллы', 'Ball tarixi': 'История баллов',
    "Mijoz qora ro'yxatda — ball yozilmaydi, chegirma berilmaydi.": 'Клиент в чёрном списке — баллы не начисляются, скидка не даётся.', "Jamg'arma karta yo'q": 'Накопительной карты нет', "Hali harakat yo'q": 'Операций ещё нет',
    'Ball miqdorini kiriting': 'Введите количество баллов', 'Ballar yangilandi': 'Баллы обновлены', "Jamg'arma karta ochildi: {0}": 'Накопительная карта открыта: {0}', 'Saqlandi': 'Сохранено', "Yuklab bo'lmadi": 'Не удалось скачать',

    // ---------- admin: sadoqat bo'limi ----------
    "Jamg'arma kartalar": 'Накопительные карты', "Mijozni bosing — profili ochiladi. Karta raqami bo'yicha ham qidiriladi.": 'Нажмите на клиента — откроется профиль. Поиск работает и по номеру карты.',
    "Kartasi yo'qlarga karta ochish": 'Открыть карты тем, у кого нет', 'Ism, mashina raqami, telefon yoki karta raqami': 'Имя, номер машины, телефон или номер карты', 'Oxirgi harakatlar': 'Последние операции',
    "🎁 Sadoqat dasturi o'chiq": '🎁 Программа лояльности выключена',
    "Yoqilsa, har bir mijozga jamg'arma karta ochiladi. Har pul to'lovidan belgilangan foiz bonus ball bo'lib qaytadi (1 ball = 1 so'm), ballar keyingi ishlarda to'lovga ishlatiladi. Mijoz ko'p xarid qilgani sari darajasi oshadi (Bronza → Kumush → Oltin → Platina) — keshbek va doimiy chegirma ko'payadi. Karta ochilishi, ball yozilishi, daraja oshishi va ballar kuyishi haqida mijozga Telegram orqali xabar boradi.":
      'Если включить, каждому клиенту откроется накопительная карта. С каждой оплаты деньгами возвращается заданный процент бонусными баллами (1 балл = 1 сум), баллами оплачиваются следующие работы. Чем больше покупок, тем выше уровень (Бронза → Серебро → Золото → Платина) — растут кешбэк и постоянная скидка. Об открытии карты, начислении баллов, повышении уровня и сгорании баллов клиент получает сообщение в Telegram.',
    "Yoqilsa, har bir mijozga": 'Если включить, каждому клиенту откроется', "jamg'arma karta": 'накопительная карта',
    "ochiladi. Har pul to'lovidan belgilangan foiz": '. С каждой оплаты деньгами возвращается заданный процент', 'bonus ball': 'бонусными баллами',
    "bo'lib qaytadi (1 ball = 1 so'm), ballar keyingi ishlarda to'lovga ishlatiladi. Mijoz ko'p xarid qilgani sari darajasi oshadi (Bronza → Kumush → Oltin → Platina) — keshbek va doimiy chegirma ko'payadi. Karta ochilishi, ball yozilishi, daraja oshishi va ballar kuyishi haqida mijozga Telegram orqali xabar boradi.":
      '(1 балл = 1 сум), баллами оплачиваются следующие работы. Чем больше покупок, тем выше уровень (Бронза → Серебро → Золото → Платина) — растут кешбэк и постоянная скидка. Об открытии карты, начислении баллов, повышении уровня и сгорании баллов клиент получает сообщение в Telegram.',
    "Pastdagi sozlamalarni tekshirib, «Dastur yoqilgan» belgisini qo'ying va saqlang.": 'Проверьте настройки ниже, поставьте галочку «Программа включена» и сохраните.', 'Dasturni direktor yoqadi.': 'Программу включает директор.',
    'Shu oy yangi: {0} · balansi borlar: {1}': 'Новых за месяц: {0} · с балансом: {1}', 'Mijozlardagi ballar': 'Баллы у клиентов', "Mijozlar oldidagi majburiyat (so'mda)": 'Обязательства перед клиентами (в сумах)',
    'Shu oy yozildi': 'Начислено за месяц', 'Keshbek va bonuslar': 'Кешбэк и бонусы', 'Shu oy ishlatildi': 'Использовано за месяц', 'Kuydi: {0} ball': 'Сгорело: {0} баллов', "Kuygan ball yo'q": 'Сгоревших баллов нет',
    '🥉 Bronza · {0}': '🥉 Бронза · {0}', '🥈 Kumush · {0}': '🥈 Серебро · {0}', '🥇 Oltin · {0}': '🥇 Золото · {0}', '💎 Platina · {0}': '💎 Платина · {0}',
    'Bronza': 'Бронза', 'Kumush': 'Серебро', 'Oltin': 'Золото', 'Platina': 'Платина', '🥉 Bronza': '🥉 Бронза', '🥈 Kumush': '🥈 Серебро', '🥇 Oltin': '🥇 Золото', '💎 Platina': '💎 Платина',
    "Xarid: {0} so'm": 'Покупки: {0} сум', 'Hali karta ochilmagan': 'Карт ещё нет', '{0} ta karta': 'Карт: {0}',
    "Bazangizdagi kartasi yo'q barcha mijozlarga (qora ro'yxatdagilardan tashqari) karta ochiladi. Telegramga ulanganlarga xabar boradi.": 'Карта откроется всем клиентам вашей базы без карты (кроме чёрного списка). Подключённым к Telegram придёт сообщение.',
    'Har biriga xush kelibsiz bonusi ({0} ball)': 'Каждому приветственный бонус ({0} баллов)', 'Karta ochish': 'Открыть карты',
    'Dastur shartlari': 'Условия программы', "Dastur hozir o'chiq.": 'Программа сейчас выключена.', 'Jami xarid': 'Всего покупок', "{0} so'm dan": 'от {0} сум', "boshlang'ich": 'начальный',
    "Ball bilan to'lash": 'Оплата баллами', 'narxning {0}% gacha': 'до {0}% цены', 'Ballar kuyishi': 'Сгорание баллов', '{0} kun kelmasa': 'если нет визитов {0} дн.', 'kuymaydi': 'не сгорают',
    'Karta ochilganda': 'При открытии карты', "Tug'ilgan kunda": 'В день рождения', 'Sozlamalar': 'Настройки', 'Dastur yoqilgan': 'Программа включена',
    "Jami xarid, so'mdan": 'Покупок от, сум', 'Keshbek %': 'Кешбэк %', 'Chegirma %': 'Скидка %', '{0} chegarasi': 'порог {0}', '{0} keshbek foizi': 'кешбэк {0}', '{0} chegirma foizi': 'скидка {0}',
    "Ball bilan to'lash, narxning %": 'Оплата баллами, % цены', 'Ballar kuyadi, kun (0 — kuymaydi)': 'Баллы сгорают, дней (0 — не сгорают)', 'Karta ochilganda bonus, ball': 'Бонус при открытии карты, баллов', "Tug'ilgan kun bonusi, ball": 'Бонус ко дню рождения, баллов',
    "Daraja mijozning sizdagi pul bilan to'lagan jami summasidan hisoblanadi. Ball bilan to'langan qismdan keshbek yozilmaydi. Qarzga qilingan ish uchun ball qarz to'langanda yoziladi.": 'Уровень считается по сумме, оплаченной клиентом деньгами у вас. С части, оплаченной баллами, кешбэк не начисляется. За работу в долг баллы начисляются при оплате долга.',
    "Daraja mijozning sizdagi pul bilan to'lagan jami summasidan hisoblanadi. Ball bilan to'langan qismdan keshbek yozilmaydi. Qarzga qilingan ish uchun ball qarz to'langanda yoziladi. Oxirgi o'zgarish: {0}, {1}.": 'Уровень считается по сумме, оплаченной клиентом деньгами у вас. С части, оплаченной баллами, кешбэк не начисляется. За работу в долг баллы начисляются при оплате долга. Последнее изменение: {0}, {1}.',
    'Misol:': 'Пример:', "🥈 Kumush mijoz, ish narxi {0} so'm.": '🥈 клиент уровня Серебро, цена работы {0} сум.',
    "Chegirma {0}%: −{1} so'm → to'laydi": 'Скидка {0}%: −{1} сум → платит', "Chegirmasiz to'laydi": 'Без скидки платит', 'Keshbek {0}%:': 'Кешбэк {0}%:',
    ". Keyingi safar {0} so'm gacha (narxning {1}%) ball bilan to'lay oladi.": '. В следующий раз сможет оплатить баллами до {0} сум ({1}% цены).',
    'Sozlamalar saqlandi': 'Настройки сохранены',

    // ---------- admin: jurnal, super admin ----------
    'Kim, qachon, nima qilgani. Oxirgi 300 ta yozuv.': 'Кто, когда и что сделал. Последние 300 записей.', "Servis bo'yicha": 'По сервису', "Hali yozuv yo'q": 'Записей ещё нет',
    'Ikki bosqichli tasdiq (Telegram)': 'Двухфакторная проверка (Telegram)', "Telegram'ga ulash": 'Подключить Telegram', "O'chirish": 'Отключить', "2FA ni o'chirish uchun parolingizni kiriting.": 'Введите пароль, чтобы отключить 2FA.',
    "2FA ni o'chirish": 'Отключить 2FA', 'IP cheklovi': 'Ограничение по IP',
    "Yoqilgan ✅ Har kirishda Telegram'ga 6 xonali kod keladi.": 'Включена ✅ При каждом входе в Telegram приходит 6-значный код.',
    "Ulangan, lekin bot hozir o'chiq (TELEGRAM_BOT_TOKEN yo'q) — kod talab qilinmaydi.": 'Подключена, но бот сейчас выключен (нет TELEGRAM_BOT_TOKEN) — код не требуется.',
    "O'chiq. Yoqish uchun «Telegram'ga ulash» → botda START bosing. Shundan keyin parol yetarli bo'lmaydi.": 'Выключена. Чтобы включить: «Подключить Telegram» → START в боте. После этого одного пароля будет недостаточно.',
    "Bot sozlanmagan (TELEGRAM_BOT_TOKEN / TELEGRAM_BOT_USERNAME), 2FA ni yoqib bo'lmaydi.": 'Бот не настроен (TELEGRAM_BOT_TOKEN / TELEGRAM_BOT_USERNAME), включить 2FA нельзя.',
    'Yoqilgan ✅ Super admin faqat SUPERADMIN_ALLOWED_IPS dagi manzillardan kira oladi.': 'Включено ✅ Супер-админ входит только с адресов из SUPERADMIN_ALLOWED_IPS.',
    "O'chiq. Yoqish uchun .env da SUPERADMIN_ALLOWED_IPS=IP1, IP2 yozib, ilovani qayta ishga tushiring.": 'Выключено. Чтобы включить, укажите в .env SUPERADMIN_ALLOWED_IPS=IP1, IP2 и перезапустите приложение.',
    "Ikki bosqichli tasdiq o'chirildi": 'Двухфакторная проверка отключена',
    "+ Servis qo'shish": '+ Добавить сервис',
    "Avval muammoli servislar. «Faol emas» — 14 kundan beri hech narsa kiritmagan. «Ishlatmayapti» — qo'shilgandan beri bitta ham ish kiritmagan.": 'Сначала проблемные сервисы. «Неактивен» — ничего не вносил 14 дней. «Не использует» — ни одной работы с момента добавления.',
    'Servis nomi': 'Название сервиса', 'Masalan: Avto Lider': 'Например: Avto Lider', 'Manzil': 'Адрес', 'Toshkent, Chilonzor 9-kvartal': 'Ташкент, Чиланзар 9-квартал', 'Direktor ismi': 'Имя директора',
    'Ism Familiya': 'Имя Фамилия', 'Direktor logini': 'Логин директора', "Direktor kirib, administrator va ustalarni o'zi qo'shadi.": 'Директор войдёт и сам добавит администраторов и мастеров.',
    "Servis qo'shildi. Login va parolni direktorga bering.": 'Сервис добавлен. Передайте директору логин и пароль.',
    'Faol servislar': 'Активные сервисы', 'Jami kiritilgan ishlar': 'Всего внесено работ', 'Mijozlar qaytib kelishi': 'Возвращаемость клиентов',
    '{0} ({1} kun oldin)': '{0} ({1} дн. назад)', "hali yo'q": 'ещё нет', 'Direktor: {0} ({1}) · xodimlar: {2}': 'Директор: {0} ({1}) · сотрудников: {2}', 'Direktor: {0} · xodimlar: {1}': 'Директор: {0} · сотрудников: {1}',
    'Ishlar:': 'Работ:', '30 kunda:': 'За 30 дней:', 'Oxirgi faollik:': 'Последняя активность:', 'Mijozlar:': 'Клиентов:', 'Vaqti kelgan:': 'Срок пришёл:', 'Qaytib kelgan:': 'Вернулись:', 'Boshqa joyga:': 'В другой сервис:', 'Kelmagan:': 'Не приехали:', 'Qaytish:': 'Возврат:',
    'Faol emas': 'Неактивен', 'Ishlatmayapti': 'Не использует', 'Kelmaganlar va sababi': 'Не приехавшие и причина', 'Yoqish': 'Включить', 'Direktor parolini tiklash': 'Сбросить пароль директора',
    "Hali servis qo'shilmagan. «+ Servis qo'shish» tugmasini bosing.": 'Сервисов ещё нет. Нажмите «+ Добавить сервис».',
    "(muddat {0}, {1} kun o'tdi)": '(срок {0}, просрочено {1} дн.)', '(muddat {0})': '(срок {0})', "Kelmagan mijoz yo'q": 'Не приехавших нет',
    "Rostdan o'chirasizmi? Yana bosing": 'Точно отключить? Нажмите ещё раз', 'Parol yangilandi. Direktor qaytadan kirishi kerak.': 'Пароль обновлён. Директору нужно войти заново.',

    // ---------- admin: obuna (oylik to'lov) ----------
    'Obunasi tugagan (bloklangan)': 'Подписка истекла (заблокированы)', '💳 Obuna:': '💳 Подписка:', "💳 Obuna to'lovi": '💳 Оплата подписки', '💳 Obuna': '💳 Подписка',
    'belgilanmagan': 'не задана', 'Tugagan · {0} · bloklangan': 'Истекла · {0} · заблокирован', 'Tugagan · {0} · imtiyoz: {1} kun': 'Истекла · {0} · льготный период: {1} дн.',
    '{0} gacha · {1} kun qoldi': 'до {0} · осталось {1} дн.', '{0} gacha · {1} kun': 'до {0} · {1} дн.',
    "· oylik to'lov": '· ежемесячная оплата', 'Holati:': 'Статус:', 'Necha oy': 'Сколько месяцев', '{0} oy': '{0} мес.',
    "masalan: naqd, Click, o'tkazma": 'например: наличные, Click, перевод',
    "Muddat {0} dan boshlab uzayadi. To'lov qabul qilingach bloklangan servis darhol ochiladi.": 'Срок продлится начиная с {0}. После приёма оплаты заблокированный сервис откроется сразу.',
    "Muddat bugundan boshlab uzayadi. To'lov qabul qilingach bloklangan servis darhol ochiladi.": 'Срок продлится начиная с сегодня. После приёма оплаты заблокированный сервис откроется сразу.',
    "💳 To'lovni qabul qilish": '💳 Принять оплату', "Muddatni qo'lda belgilash": 'Задать срок вручную', 'masalan: sinov uzaytirildi': 'например: продлён пробный период',
    '· {0} oy · {1} — {2} · {3}': '· {0} мес. · {1} — {2} · {3}', "To'lov qabul qilindi, muddat uzaytirildi": 'Оплата принята, срок продлён', "Obuna muddati o'zgartirildi": 'Срок подписки изменён',
    '⏳': '⏳', "Obuna {0} gacha": 'Подписка до {0}', "· {0} kun qoldi. Oylik to'lov {1} so'm. Super adminga to'lab, muddatni uzaytiring — aks holda panel yopiladi.": '· осталось {0} дн. Ежемесячная оплата {1} сум. Оплатите супер-админу и продлите срок — иначе панель закроется.',
    'Obuna muddati {0} da tugadi.': 'Срок подписки истёк {0}.', "Imtiyoz kunlari: yana {0} kun, keyin panelga kirish yopiladi. Oylik to'lov {1} so'm — super adminga to'lang.": 'Льготный период: ещё {0} дн., затем вход в панель закроется. Ежемесячная оплата {1} сум — оплатите супер-админу.'
  };

  /** Qolip bo'lmagan, lekin tarkibida uchraydigan qismlar — oxirgi chora (butun so'z sifatida). */
  const PARTIALS = [
    ["so'm", 'сум'], ['ball', 'баллов'], ['kun qoldi', 'дн. осталось'], ["kun o'tdi", 'дн. просрочено'], ['kun', 'дн.'],
    ["to'langan:", 'оплачено:'], ['shundan ball', 'из них баллами'], ['chegirma −', 'скидка −'], ["to'liq to'langan", 'оплачено полностью'],
    ['muddat', 'срок'], ['qarz', 'долг'], ['bajarildi', 'выполнено'], ['keyingisi', 'следующая'], ['usta:', 'мастер:'], ['probeg', 'пробег'],
    ['sizda', 'у вас'], ['boshqa joyda', 'в другом месте'], ['balans', 'баланс'], ['karta', 'карта'], ['chegirma', 'скидка'], ['Keshbek', 'Кешбэк'],
    ['darajagacha', 'до уровня'], ['Shu oy:', 'За месяц:']
  ];

  // =====================================================================
  //  Dvigatel
  // =====================================================================
  const exact = new Map();
  const templates = [];
  const escRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  Object.keys(RU).forEach((k) => {
    const nk = k.replace(/\s+/g, ' ').trim();
    if (/\{\d+\}/.test(nk)) {
      const order = [];
      const re = new RegExp('^' + nk.split(/(\{\d+\})/).map((p) => {
        const m = p.match(/^\{(\d+)\}$/);
        if (m) { order.push(+m[1]); return '([\\s\\S]+?)'; }
        return escRe(p);
      }).join('') + '$');
      templates.push({ re, v: RU[k], order, len: nk.length });
    } else {
      exact.set(nk, RU[k]);
    }
  });
  templates.sort((a, b) => b.len - a.len); // aniqroq (uzunroq) qoliplar avval
  const partials = PARTIALS.map(([a, b]) => [new RegExp('(?<![\\p{L}\\p{N}])' + escRe(a) + '(?![\\p{L}\\p{N}])', 'gu'), b]);

  function translate(s) {
    if (lang !== 'ru' || !s) return s;
    const m = s.match(/^(\s*)([\s\S]*?)(\s*)$/);
    const core = m[2].replace(/\s+/g, ' ');
    if (!core) return s;
    let out = exact.get(core);
    if (out === undefined) {
      for (const t of templates) {
        const mm = core.match(t.re);
        if (mm) {
          out = t.v.replace(/\{(\d+)\}/g, (_, i) => { const idx = t.order.indexOf(+i); return idx >= 0 ? mm[idx + 1] : ''; });
          break;
        }
      }
    }
    if (out === undefined) {
      let p = core, changed = false;
      for (const [re, rep] of partials) { const n = p.replace(re, rep); if (n !== p) { p = n; changed = true; } }
      if (!changed) return s;
      out = p;
    }
    return m[1] + out + m[3];
  }
  window.T = translate;

  const ATTRS = ['placeholder', 'title', 'aria-label', 'alt'];
  function walk(node) {
    if (node.nodeType === 3) {
      const v = translate(node.nodeValue);
      if (v !== node.nodeValue) node.nodeValue = v;
      return;
    }
    if (node.nodeType !== 1 || node.tagName === 'SCRIPT' || node.tagName === 'STYLE') return;
    for (const a of ATTRS) {
      if (node.hasAttribute(a)) { const v = translate(node.getAttribute(a)); if (v !== node.getAttribute(a)) node.setAttribute(a, v); }
    }
    for (let c = node.firstChild; c; c = c.nextSibling) walk(c);
  }

  if (lang === 'ru') {
    walk(document.body);
    if (document.title) document.title = translate(document.title);
    new MutationObserver((muts) => {
      for (const mu of muts) {
        if (mu.type === 'characterData') walk(mu.target);
        else if (mu.type === 'attributes') walk(mu.target);
        else mu.addedNodes.forEach(walk);
      }
    }).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  }

  window.setLang = function (l) {
    try { localStorage.setItem(KEY, l); } catch (e) {}
    location.reload(); // tarjima DOM da — asl matnga qaytish uchun sahifa yangilanadi
  };

  // Til tanlash: o'ng yuqori burchakda kichik tugmalar (chop etishda ko'rinmaydi)
  const sw = document.createElement('div');
  sw.id = 'langSwitch';
  sw.setAttribute('aria-label', 'Til / Язык');
  sw.style.cssText = 'position:fixed;top:10px;right:12px;z-index:60;display:flex;gap:2px;background:rgba(255,255,255,.92);border:1px solid rgba(0,0,0,.12);border-radius:999px;padding:3px;font:700 12px/1 system-ui,sans-serif;box-shadow:0 4px 14px rgba(0,0,0,.15)';
  [['uz', "O'Z"], ['ru', 'RU']].forEach(([code, label]) => {
    const b = document.createElement('button');
    b.type = 'button'; b.textContent = label;
    b.style.cssText = 'border:0;border-radius:999px;padding:6px 10px;cursor:pointer;width:auto;font:inherit;' +
      (code === lang ? 'background:#1c2b33;color:#fff' : 'background:transparent;color:#1c2b33');
    b.onclick = () => { if (code !== lang) window.setLang(code); };
    sw.appendChild(b);
  });
  document.body.appendChild(sw);
  const st = document.createElement('style');
  st.textContent = '@media print { #langSwitch { display: none !important; } }';
  document.head.appendChild(st);
})();
