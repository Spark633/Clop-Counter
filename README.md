# Clop Counter

Android-App (Kotlin, Jetpack Compose, Room) zum täglichen Zählen: Jeden Tag startet der Zähler mit dem ersten Eintrag bei **#1** und zählt dann hoch (#2, #3, …).

## Funktionen

- **Heute**: großer Zähl-Button, die heutigen Einträge, Gesamtzahl, Rekord und aktuelle Serie. Ein neuer Eintrag lässt sich per „Rückgängig“ zurücknehmen.
- **Kalender**: Monatsansicht, Farbintensität je nach Anzahl. Ein Tipp auf einen Tag öffnet die Tagesansicht (dort kann man auch nachträglich eintragen).
- **Verlauf**: alle Einträge nach Tag gruppiert, mit Volltextsuche und Tag-Filter.
- **Statistik**: Gesamt, aktive Tage, Ø pro Tag, Rekord, aktuelle/längste Serie, wie oft #1/#2/#3 … erreicht wurde, Tage nach Anzahl, letzte 30 Tage, Wochentage, Top-Tags.
- **Eintrag-Details**: Notiz (speichert automatisch), Tags, Bilder/Videos, Dateien und Links anhängen, Datum/Uhrzeit ändern, löschen.

Die Nummer eines Eintrags wird aus der Reihenfolge innerhalb des Tages berechnet. Wird ein Eintrag gelöscht oder zeitlich verschoben, rücken die anderen automatisch nach.

Ohne Konto bleiben alle Daten lokal auf dem Gerät. Angehängte Medien werden in den App-Speicher kopiert, damit sie auch dann erhalten bleiben, wenn das Original aus der Galerie gelöscht wird.

## Konto & Freunde (optional)

- **Konto** nur mit Nutzername + Passwort (keine E-Mail). Jedes Konto bekommt eine **User-Nr.** (ab #10001) und einen **Wiederherstellungscode** zum Zurücksetzen des Passworts.
- **Sync & Backup**: Einträge, Tags, Links und Notizen werden mit dem Server abgeglichen und lassen sich so auf ein neues Handy übertragen. **Bilder, Videos und Dateien bleiben nur auf dem Gerät.**
- **Freunde** per Nutzername oder `#Nr.` hinzufügen; erst nach Annahme sieht man gegenseitig Statistik, Verlauf und die Galerie der geteilten Links. **Notizen sind privat** und für Freunde nie sichtbar.

### Server einrichten (Supabase, kostenloser Tarif reicht)

1. Auf https://supabase.com ein Projekt anlegen.
2. Im Dashboard **SQL Editor → New query**, den Inhalt von [`supabase/schema.sql`](supabase/schema.sql) einfügen und **Run**.
3. Unter **Project Settings → API** die **Project URL** und den **anon public key** kopieren.
4. `supabase.properties.example` nach `supabase.properties` kopieren und beide Werte eintragen. Beide sind öffentlich gedacht; die Daten schützen die Zugriffsregeln (Row Level Security) aus dem Schema.

Ohne `supabase.properties` funktioniert die App wie bisher rein lokal.

## Bauen

**Android Studio:** Projektordner öffnen, Gerät/Emulator wählen, ▶ Run.

**Kommandozeile** (JDK 17+ und Android SDK nötig):

```
./gradlew assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

**GitHub Actions:** Bei jedem Push baut `.github/workflows/build.yml` die Debug-APK. Sie lässt sich im jeweiligen Workflow-Lauf unter „Artifacts“ herunterladen.

## Aufbau

```
app/src/main/java/de/clopcounter/
├── ClopApp.kt / MainActivity.kt
├── data/      Room-Entities, DAO, Datenbank, Repository, Nummerierung & Statistik
└── ui/        ViewModel, Navigation, gemeinsame Komponenten, Theme
    └── screens/  Heute, Kalender, Tag, Verlauf, Statistik, Eintrag-Details
```
