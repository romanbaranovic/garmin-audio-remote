# Garmin Edge Audio Remote

🇬🇧 [Read this in English](README.en.md)

Vlastný Connect IQ widget pre cyklopočítače Garmin Edge (530, 830, 1030,
1030 Plus), ktorý cez sprievodnú Android appku ovláda prehrávanie
audiokníh a podcastov (Smart AudioBook Player, Pocket Casts) priamo z
displeja hodiniek — play/pauza, -10s, +10s, skok na ďalšiu položku vo
fronte, s progress barom a názvom titulu.

## Prečo to existuje

Staršie generácie Edge (530/830/1030) nemajú natívny "Music Controls"
widget (ten pribudol až od 540/840). Garmin navyše blokuje tretím stranám
posielanie Bluetooth AVRCP príkazov (play/pause/seek) do systémového
prehrávača telefónu — takže bežný Connect IQ widget by sa k prehrávaču
vôbec nedostal.

## Ako to funguje

Riešenie obchádza AVRCP blok tak, že vôbec Bluetooth médiové príkazy
nepoužíva — pozostáva z dvoch častí, ktoré musia bežať súčasne:

- `edge-widget/` — Connect IQ widget (Monkey C), zobrazuje titul,
  progress bar a 4 tlačidlá (-10s, next-in-queue, +10s, play/pause).
- `android-companion/` — Android appka (Kotlin), ktorá beží na pozadí
  telefónu a cez `MediaSessionManager`/`NotificationListenerService`
  priamo ovláda `MediaSession` cieľovej appky — to je systémové API, nie
  Bluetooth, takže Garminov AVRCP blok sa netýka.

Widget a appka spolu komunikujú cez `Toybox.Communications` / Garmin
Connect IQ Mobile SDK, napojené na existujúce párovanie hodiniek v appke
Garmin Connect Mobile.

## 1. Widget na Edge 830

### Predpoklady

- [Connect IQ SDK Manager](https://developer.garmin.com/connect-iq/sdk/) —
  stiahni a nainštaluj, prihlás sa Garmin Connect Developer účtom
  (netreba platený developer účet, stačí bežný Garmin účet).
- V SDK Manageri nainštaluj SDK aspoň vo verzii zodpovedajúcej
  `minSdkVersion="3.2.0"` z manifestu (aktuálne SDK to spĺňa automaticky).
- Vygeneruj vývojárske podpisovacie kľúče cez SDK Manager (Utilities →
  Generate Developer Key), ak ich ešte nemáš.

### Build a sideload

```bash
cd ~/garmin-audio-remote/edge-widget

# Skompiluj do .prg (over si presnú cestu k monkeyc vo svojej inštalácii SDK)
monkeyc -f monkey.jungle -o bin/AudioRemote.prg \
    -y ~/.Garmin/ConnectIQ/developer_key.der \
    -d edge830

# Priprav Edge 830: USB kábel do počítača, zobrazí sa ako disk GARMIN
cp bin/AudioRemote.prg /Volumes/GARMIN/GARMIN/Apps/
```

Po odpojení USB kábla by mal widget pribudnúť do widget loopu (tlačidlo
UP/DOWN na Edge 830 alebo swipe, keďže má dotykovku). Keďže ide o sideload
(nie Connect IQ Store), Garmin Connect Mobile appka widget nebude
spravovať/aktualizovať — nové verzie treba znova skopírovať cez USB.

**Poznámka k UUID:** manifest používa `id="7ce36164-617d-483b-8c29-c1eff82bc95c"`.
Táto hodnota (bez pomlčiek: `7ce36164617d483b8c29c1eff82bc95c`) je natvrdo
zadaná aj v Android appke (`Constants.kt` → `WATCH_APP_ID`) — **musia sa
zhodovať**, inak si watch a telefón nenájdu appky navzájom cez Connect IQ
Mobile SDK. Ak by si niekedy manifestové UUID menil, over si, že si zmenu
premietol aj do `Constants.kt`.

## 2. Android companion appka

### Predpoklady

- Android Studio (najnovšia stabilná verzia).
- Telefón s nainštalovanou appkou **Garmin Connect Mobile**, s Edge 830 už
  spárovaným v nej (Connect IQ Mobile SDK sa vezie na tomto párovaní —
  vlastnú appku netreba párovať samostatne).
- Nainštalovaný **Smart AudioBook Player** a/alebo **Pocket Casts**.

### Build

```bash
cd ~/garmin-audio-remote/android-companion
```

Otvor priečinok v Android Studiu ("Open" → vyber `android-companion/`).
Android Studio pri prvom otvorení automaticky dogeneruje `gradle-wrapper.jar`
a wrapper skripty (chýbajú v tomto repozitári, keďže ide o binárny súbor).
Po synchronizácii zostav a nainštaluj appku na telefón cez Run ▶ v Android
Studiu, alebo príkazovým riadkom:

```bash
./gradlew installDebug
```

### Nastavenie po inštalácii (jednorazovo)

1. Otvor appku "Audio Remote" na telefóne.
2. Klikni **"Povoliť prístup k notifikáciám"** → v systémovom nastavení
   nájdi "Audio Remote" a zapni. Bez tohto appka nevidí, čo hrá v iných
   appkách (`MediaSessionManager` to vyžaduje).
3. Klikni **"Vypnúť optimalizáciu batérie pre appku"** → potvrď. Inak Android
   po čase uspí foreground service a widget prestane dostávať aktualizácie.
4. Appka po návrate na hlavnú obrazovku automaticky naštartuje
   `AudioRemoteService` (foreground service, vidno ho ako trvalú notifikáciu
   "Audio Remote beží").

### Ako appka vyberá, ktorý prehrávač ovláda

Automaticky: ak medzi aktívnymi MediaSessions na telefóne hrá (STATE_PLAYING)
Smart AudioBook Player alebo Pocket Casts, appka ovláda ten. Ak ani jeden
nehrá, ale jeden z nich má aktívnu (pozastavenú) session, ovláda toho. Ak
nič z whitelisted appiek nemá session vôbec, appka ako fallback skúsi
ktorúkoľvek inú appku, čo práve hrá — takže widget nezostane úplne mŕtvy aj
pri inom prehrávači, len bez záruky správania.

## 3. Overenie funkčnosti

1. Spusti prehrávanie v Smart AudioBook Player alebo Pocket Casts na
   telefóne.
2. Otvor widget "Audio Remote" na Edge 830 (widget loop).
3. Mal by sa zobraziť názov kapitoly/titulu a progress bar sa má do pár
   sekúnd (max ~3s, appka pollinguje) zosynchronizovať.
4. Ťuknutím na play/pause, -10s, +10s over, že sa príkaz prejaví v
   prehrávači na telefóne.

### Debug

- Widget: `monkeydo bin/AudioRemote.prg edge830` v simulátore, alebo
  `adb logcat` na strane telefónu pre `AudioRemoteService`/`ConnectIQ` logy.
- Ak sa widget a appka nespárujú: skontroluj, že Edge 830 je spárovaný v
  Garmin Connect Mobile a že Bluetooth je zapnutý na oboch stranách; appka
  hľadá `connectIQ.knownDevices` až po `onSdkReady()` callbacku.
- Ak progress bar na hodinkách "trhá" alebo zaostáva: watch si medzi
  správami polohu lokálne dopočítava (`System.getTimer()` interpolácia),
  takže krátke oneskorenie pri manuálnom seeku v telefónnej appke je
  očakávané, kým nepríde ďalší update z pollingu (~3s).

## Obmedzenia / known limitations

- Sideloadovaný widget sa neaktualizuje cez Connect IQ Store — nová verzia
  vyžaduje opätovné USB kopírovanie `.prg` súboru.
- Ak sa v čase behu appky vypne Bluetooth alebo Edge 830 odíde mimo dosahu,
  `connectIQ.sendMessage()` zlyhá potichu (viď `AudioRemoteService.kt`
  catch blok) — appka to skúsi znova pri ďalšom poll tickte, akonáhle sa
  spojenie obnoví.
- Balíčkové ID `ak.alizandro.smartaudiobookplayer` (Smart AudioBook Player) a
  `au.com.shiftyjelly.pocketcasts` (Pocket Casts) sú overené cez Google Play
  (júl 2026). Pozor, appka `de.ph1b.audiobook` je iná aplikácia ("Voice
  Audiobook Player") — pôvodne som ju omylom zamenil so Smart AudioBook
  Player, `Constants.kt` už má správne ID.
