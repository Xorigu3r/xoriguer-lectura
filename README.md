# Lectura · Xoriguer

Lector de llibres electrònics gratuït i de codi obert per a **Android, Windows i iPhone**, fet amb Kotlin Multiplatform i Compose Multiplatform.

![logo](empaquetar/logo/logo-1200.png)

## Què fa (versió 0.1)
- Troba els llibres del dispositiu i els mostra en una biblioteca amb portades, en quadrícula o en llista.
- Cerca per títol o autor, filtres per format, "Continua llegint" i ordenació.
- Llegeix **EPUB, FB2, TXT i PDF**.
- Temes Nit, Sèpia, Dia i Gris; mida de lletra, interlineat, marges, lletra amb serif o sense, text justificat.
- Índex de capítols, barra de posició i desat del punt de lectura.

## Properament
Cites i notes amb colors, marcadors, més formats (MOBI, AZW3, CBZ, DJVU, DOCX…), diccionari, fonts pròpies, miniatures de pàgines, lectura en veu alta i sincronització amb Google Drive.

## Compilar
Cal el JDK 17 i, per a Android, l'SDK d'Android.

```
.\gradlew :composeApp:run              # Windows
.\gradlew :composeApp:packageMsi       # instal·lador de Windows
.\gradlew :composeApp:assembleDebug    # APK d'Android
```

L'iPhone es compila des d'un Mac amb Xcode.

## Baixades
A la pàgina de [Releases](https://github.com/Xorigu3r/xoriguer-lectura/releases):
- **Android**: `LecturaXoriguer.apk`.
- **Windows**: `LecturaXoriguer-Windows.zip`. Descomprimeix-lo i obre `Lectura Xoriguer.exe`.

És una versió beta: encara no s'ha provat en un mòbil.

## Llicència
[GPL-3.0](LICENSE). El nom Xoriguer i el logo del falcó són de Xoriguer.

## Llibreries
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) (Apache-2.0)
- [Apache PDFBox](https://pdfbox.apache.org/) (Apache-2.0), per als PDF a Windows
