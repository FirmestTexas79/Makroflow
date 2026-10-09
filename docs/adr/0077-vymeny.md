# 0077 – Výměny Makromonů

Stav: přijato (2026-10-09), větev `MULTI`

Výměna mezi dvěma hráči přes kód, bez Cloud Functions (nepotřebuje tarif Blaze).

## Proč bez serverové funkce

Pravdou o Makromonech je lokální databáze v telefonu (cloud je záloha). Serverová funkce by
upravenému telefonu stejně nezabránila Makromona si nechat – k tomu by musel inventář žít na serveru.
Pro poctivé hráče stačí, aby výměna byla **atomická a dokončitelná**: dokument `trades/{kód}`
se stavovým automatem, který hlídají pravidla Firestore, a idempotentní provedení v telefonu.

## Průběh

1. A vybere Makromona → založí `trades/{kód}` (6 znaků bez 0/O/1/I/L, obsazený kód pravidla
   nedovolí přepsat → zkusí se jiný). Kód ukáže kamarádovi.
2. B v Aréně dá „Mám kód“, uvidí nabídku A, vybere svého → připojí se (`b`, `offerB`, stav `offered`).
3. Každý potvrdí svou stranu (`confirmA` / `confirmB`). Zrušit jde, dokud nepotvrdili oba.
4. Obě potvrzení = výměna platí. Každý telefon u sebe odebere svého (jako puštění, včetně týmu a
   parťáka) a přidá cizího se **stejným uid**, levelem, XP, útoky a shiny, s `otName` (původní trenér).
   Pak zapíše `appliedA` / `appliedB`. Provedení je idempotentní (podle uid); když spadne síť,
   dokončí se při dalším otevření Arény.

Pravidla: číst volnou výměnu smí každý, kdo zná kód; jinak jen účastníci. Každý mění jen své
klíče, potvrzení jde jen ve stavu `offered`, „provedeno“ jen po obou potvrzeních, mazat nelze.

## Ochrany

- Nabídka se ověřuje stejně jako duch v aréně (druh, level 1–30, útoky z poolu druhu, XP v rámci levelu).
- Nabídnout nejde zamčeného Makromona ani posledního, kterého hráč má.
- Nabídnutého Makromona nejde pustit, dokud výměna běží.

## Vývoj výměnou

`Species.tradeEvolvesTo` – druh se při výměně vyvine v jiný. Zatím u žádného druhu nastaveno
(rozhodne autor světa). Karta „DOSTANEŠ“ to dopředu ukáže, po výměně se ukáže „✨ vyvinul se“.

## Původní trenér

`CapturedMakromonEntity.otName` (stejná migrace 40 → 41 jako uid). V Kapse: „Z výměny od X“.

## Co zatím ne

QR kód (kód se opisuje), výměna na dálku přes seznam přátel, real-time PvP.
