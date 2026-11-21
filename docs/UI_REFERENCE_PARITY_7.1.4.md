# VORTIX 7.1.4 — UI Reference Parity & Badge Hotfix

## Referans kaynağı

Kullanıcının yüklediği `ggg.zip` arşivi incelendi. Arşiv üç ekran görüntüsü içeriyor: `Ozet.png`, `Kontrol.png` ve `Radar.png`; Kotlin/Android proje kaynağı içermiyor. Görseller `docs/ui-reference/` altına aynen eklendi ve üç sekmenin yerleşim, başlık, kart sırası, koyu cam yüzeyler, bordo üst arka plan, alttaki yeşil ton ve opak üç öğeli dock görünümünü korumak için görsel referans olarak kullanıldı.

## Rozet yerleşimi

- Sol başlık alanı `Modifier.weight(1f).widthIn(min = 0.dp)` kullanır.
- Sağ rozet `Modifier.widthIn(min = 88.dp, max = 104.dp)` ile minimum alanı korur.
- Rozet metni `fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)`, `maxLines = 1`, `softWrap = false` ve `TextOverflow.Ellipsis` kullanır.
- Termal açıklama tek satırdır; aktif uyarının nedeni rozetin daralmasına neden olmaması için aşağıdaki satırda gösterilir.

## UYARI kuralları

Kırmızı `UYARI` yalnızca şu gerçek koşullarda gösterilir:

1. Android sistem termal durumu `MODERATE`, `SEVERE`, `CRITICAL`, `EMERGENCY` veya `SHUTDOWN`.
2. Termal uyarı açıkken geçerli pil sıcaklığının geçerli kullanıcı eşiğine (36–48°C) ulaşması/aşması.
3. Düşük pil uyarısı açıkken cihazın `DISCHARGING` olması ve pil seviyesinin geçerli eşikten (10–40%) düşük olması.

`NONE` ve `LIGHT` durumları uyarı değildir. Şarj hedefinin tamamlanması beklenen bir kullanıcı olayıdır; kırmızı anomali rozeti oluşturmaz. Tek başına OEM `BATTERY_HEALTH_OVERHEAT` bayrağı da geçerli, eşik altı sıcaklık ölçümünü geçersiz kılmaz. Sıcaklık ölçümü yokken bu sağlık bayrağına dayanarak kırmızı uyarı üretilmez; Android MODERATE+ termal durumu yine uyarıdır.

## Test kapsamı

`BatteryAlertEvaluatorTest`: 15 test metodu. Normal 24°C / `THERMAL_NONE` / %80 şarj, hedefe ulaşmış şarj, stale OEM health bayrağı, MODERATE+ uyarıları, LIGHT/None, yüksek sıcaklık, düşük pil, eşik sınırları ve bilinmeyen termal kodlar kapsanır.

- Yerel JUnit4 API uyumlu test koşucusu: 15/15 geçti.
- Kotlin/JVM smoke testi: 6/6 geçti.
- Gradle 9.6.1 wrapper denemesi: `services.gradle.org` DNS çözümlemesi başarısız olduğu için dağıtım indirilemedi. Dolayısıyla bu ortamda Android/Compose derlemesi, tam Gradle test seti, APK veya Firebase Test Lab sonucu doğrulanamadı.

## Korunan dosyalar

- `app/src/main/aidl/com/vortix/app/data/battery/shizuku/IWakelockDiagnosticsService.aidl` içindeki `void destroy();` bildirimi transaction ID olmadan korundu.
- `app/src/main/java/com/vortix/app/feature/insights/BatteryInsightsPanel.kt` içindeki `import com.vortix.app.platform.UsageAccessSettingsLauncher` korundu.
- `.github/workflows/build.yml` değiştirilmedi.
- `minSdk 26`, `targetSdk 36`, package `com.vortix.app` korundu.

Sürüm: **7.1.4**, `versionCode = 20`.
