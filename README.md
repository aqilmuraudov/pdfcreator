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
