# PDF Creator: Android

Şəkillərdən vahid PDF yaradan native Android tətbiqi (Kotlin + Jetpack Compose, Material 3).

## Funksiyalar
- **Mənbələr:** qalereya (Photo Picker, icazəsiz), fayllar (limitsiz çoxlu seçim), **sənəd skaneri** (ML Kit: kamera, kənarlar avtomatik kəsilir), **mövcud PDF-i idxal** (səhifələri əlavə olunur, yəni PDF birləşdirmə)
- Başqa tətbiqdən paylaşmaq: Qalereya → Paylaş → PDF Creator
- Grid önizləmə, **basıb-saxlayıb sürüşdürərək sıralama**, döndərmə, silmə, tam ekran baxış, sıranı tərsinə çevirmə, hamısını döndərmə
- **Səhifə:** A4 / Letter / Legal / A5 / A3 / şəklin öz ölçüsü; istiqamət (Avto/Portret/Albom); hər səhifədə 1, 2 və ya 4 şəkil; Sığdır və ya Doldur; kənar boşluq
- **Rəng:** orijinal, boz, "Sənəd" (ağ-qara, yüksək kontrast)
- **Keyfiyyət:** 4 səviyyə, təxmini fayl ölçüsü göstərilir
- Səhifə nömrələri, **su nişanı** (istənilən dildə mətn), **şifrə ilə qoruma** (RC4 128-bit)
- Fonda yaratma (WorkManager), bildirişdə faiz göstərilir və ləğv etmək olur. 1000+ şəkil də yaddaşı doldurmur, çünki şəkillər PDF-ə bir-bir yazılır.
- PDF-lər `Download/PDF Creator` qovluğuna saxlanılır. Tarixçədən açmaq, paylaşmaq və silmək olur.
- Dillər: **Azərbaycan, English, Русский** (tətbiq daxilində seçim), açıq və tünd mövzu
- İnternet lazım deyil. Şəkillər telefondan çıxmır.

## Texniki qeydlər
- Android-in `PdfDocument` API-si şəkilləri sıxılmamış saxlayır (hər foto ~3–5 MB). Ona görə layihədə **öz PDF yazıcımız** var (`pdf/SimplePdfWriter.kt`): JPEG-ləri birbaşa yerləşdirir və fayllar 10–20 dəfə kiçik alınır. Hər səhifə dərhal diskə yazılır.
- minSdk 26 (Android 8.0), targetSdk 35.

## APK necə yığılır

### Variant A: Android Studio (ən sadə)
1. Android Studio-da **File → Open** seçib bu `PDFCreator` qovluğunu açın.
2. Gradle sinxronizasiyası bitəndən sonra telefonu USB ilə qoşun və ▶ **Run** düyməsini basın.
3. Paylaşmaq üçün APK lazımdırsa: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   İmzalanmış release üçün terminalda `./gradlew assembleRelease` işlədin; nəticə `app/build/outputs/apk/release/` qovluğunda olacaq.

### Variant B: GitHub (kompüter lazım deyil)
1. GitHub-da yeni repo yaradın və bu qovluğu ora yükləyin.
2. **Actions** avtomatik işə düşür və təxminən 5 dəqiqəyə APK yığılır.
3. Repo-nun **Releases** bölməsində `PDF-Creator-1.0.X.apk` görünəcək. Onu telefondan açıb quraşdırın.

## İmza açarı (vacib)
`signing/release.jks` sizin tətbiqinizin imza açarıdır. Şifrəsi `keystore.properties` faylındadır.
- **Bu faylları itirməyin və heç kimə verməyin.** Play Store-da yeniləmə buraxmaq üçün eyni açar lazım olacaq.
- `.gitignore` onları GitHub-a yükləməyə qoymur. GitHub-da öz açarınızla imzalamaq istəsəniz, repo **Settings → Secrets** bölməsinə `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` əlavə edin. Əlavə etməsəniz, APK test açarı ilə imzalanır.

## Növbəti addımlar (plan)
- Reklam: AdMob (banner + nəticə ekranında interstitial)
- OCR (axtarıla bilən mətn), şəkil kəsmə (crop), Play Store üçün ekran şəkilləri və təsvir
