# Lectura · Xoriguer

Lector de llibres electrònics gratuït i de codi obert per a **Android, Windows i iPhone**, fet amb Kotlin Multiplatform i Compose Multiplatform.

![logo](empaquetar/logo/logo-1200.png)

## Què fa (versió 0.3)
**Formats:** EPUB, PDF, Kindle (MOBI, AZW, AZW3 sense DRM), FB2, Word (DOCX), LibreOffice (ODT), RTF, TXT i còmics CBZ.

**Biblioteca**
- Troba sola els llibres del dispositiu, amb portades, títols i autors.
- 4 vistes (quadrícula, miniatures, completa i breu), cerca, filtres per format i «Continua llegint».
- Agrupa per autor, sèrie, format, carpeta o col·lecció. Col·leccions pròpies.
- Troba llibres duplicats. Canvia el nom dels fitxers, mou-los a una altra carpeta o esborra'ls.

**Llibres gratuïts:** cerca i baixa llibres de domini públic de Viquitexts, Wikisource, Project Gutenberg o de qualsevol catàleg OPDS (per exemple, un servidor de Calibre).

**Lectura**
- Temes Nit, Sèpia, Dia i Gris; mida de lletra, interlineat, marges i text justificat. També **fonts pròpies** (.ttf/.otf).
- **Per pàgines, com un llibre:** «Pàgina 12 de 340» i el capítol a sota, i cada capítol comença en una pàgina nova.
- Per passar pàgina: fletxes ‹ ›, tocar els costats, les tecles de fletxa (ordinador) o els botons de volum (Android).
- Efecte de passar pàgina: girar, cobrir, lliscar o sense efecte. Si ho prefereixes, també hi ha el desplaçament continu.
- **Lectura en veu alta**, també en segon pla i amb la pantalla bloquejada (Android).
- **Cites** amb 6 colors, **notes** i **marcadors**. La secció «Cites i notes» les recull de tots els llibres.
- **Diccionari** (Viquidiccionari / Wiktionary). La secció «Diccionari» recull totes les paraules buscades.
- **Miniatures de les pàgines** als PDF i còmics.

**Còpia i sincronització:** desa el progrés, les cites, les col·leccions i el diccionari en una carpeta que el núvol ja sincronitzi (Google Drive per a ordinador, Dropbox, OneDrive…). Es combinen les dades de tots els dispositius.

## Encara no
- Formats CBR, DJVU, DOC antic i CHM.
- Sincronització directa amb el compte de Google Drive: cal registrar l'app a Google.
- A l'iPhone: EPUB, PDF, veu i fonts pròpies (cal un Mac per compilar-ho i provar-ho).

## Compilar
Cal el JDK 17 i, per a Android, l'SDK d'Android.

```
.\gradlew :composeApp:run              # Windows
.\gradlew :composeApp:packageMsi       # instal·lador de Windows
.gradlew :composeApp:assembleRelease  # APK d'Android (signat amb la clau de %USERPROFILE%.lectura-xoriguer)
```

L'iPhone es compila des d'un Mac amb Xcode.

## Baixades
A la pàgina de [Releases](https://github.com/Xorigu3r/xoriguer-lectura/releases):
- **Android**: `LecturaXoriguer.apk`.
- **Windows**: `LecturaXoriguer-Windows.zip`. Descomprimeix-lo i obre `Lectura Xoriguer.exe`.

És una versió beta: encara no s'ha provat en un mòbil.

## Llicència
[GPL-3.0](LICENSE). El nom Xoriguer i el logo del falcó són de Xoriguer.

## Fonts de llibres i dades
- [Viquitexts / Wikisource](https://ca.wikisource.org) i [ws-export](https://ws-export.wmcloud.org): textos de domini públic.
- [Project Gutenberg](https://www.gutenberg.org): llibres de domini públic.
- [Viquidiccionari / Wiktionary](https://ca.wiktionary.org): definicions (CC BY-SA).

## Llibreries
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) (Apache-2.0)
- [Apache PDFBox](https://pdfbox.apache.org/) (Apache-2.0), per als PDF a Windows
