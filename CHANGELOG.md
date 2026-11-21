# Changelog

Alle wichtigen Änderungen an diesem Projekt. Format orientiert sich an
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach
[Semantic Versioning](https://semver.org/lang/de/).

## [1.3.1] – 2026-11-21

Nachbesserung zu 1.3.0: Die Werkbank-GUI bleibt jetzt offen und voll benutzbar.

### Behoben

* 1.3.0 hatte den Screen nach dem Abfangen des `currentItem`-Crash sofort wieder
  geschlossen (`setScreen(null)`), sodass man die `cgm:workbench` gar nicht öffnen
  konnte. Ursache war eine Field-Suche über `Class.forName` mit dem falschen
  ClassLoader – sie lieferte kein Feld, sodass der Schließ-Fallback griff.
* Die Field-Suche läuft jetzt über `instance.getClass()` und damit immer gegen die
  tatsächlich geladene CGM-Klasse.

### Geändert

* Dekompilierte Feldnamen von `cgm-forge-1.19.2-1.3.7.jar` berücksichtigt: Es gibt
  **kein** `filteredRecipes` und kein `currentItem`-Feld. Die Rezepte liegen in
  `WorkbenchScreen.tabs` → `WorkbenchScreen$Tab.items`; `currentItem` ist nur eine
  lokale Variable aus `getfield displayStack`.
* `CgmWorkbenchScreenMixin` bereinigt in `init`, `render` (`m_6305_`) und `renderBg`
  (`m_7286_`) alle Rezepteinträge mit `null`-Ergebnis aus den Tab-Listen, setzt ein
  fehlendes `displayStack` auf das erste gültige Rezept-Item bzw. `ItemStack.EMPTY`
  und fängt `loadItem(int)` bei defekten/out-of-range Rezepten ab.
* `currentItem.m_41786_()` wird weiterhin null-sicher abgefangen – ohne Screen-Close.
* `setScreen(null)` ist jetzt der allerletzte Notanker und läuft nur noch, wenn trotz
  Reparatur eine unerwartete Exception fliegt.
* Version in `gradle.properties` auf 1.3.1 erhöht.

## [1.3.0] – 2026-11-21

Neuer Client-Crash aus der Instanz `Wolf 3 CurseForge Profil-1.14_` beim Öffnen der
Waffen-Werkbank (`cgm:workbench`) wird abgefangen und diagnostiziert.

### Behoben

* `NullPointerException: Cannot invoke "ItemStack.m_41786_()" because "currentItem" is null`
  in `com.mrcrayfish.guns.client.screen.WorkbenchScreen.m_7286_` (Zeile 407; called from
  `m_6305_`). Der neue `CgmWorkbenchScreenMixin` (`require = 0`) prüft bei `init`, `render`
  und `renderBg`, ob das Rezept-Ergebnis (`displayStack` → lokale Variable `currentItem`)
  null ist, und setzt es auf `ItemStack.EMPTY`. Zwei Redirects sichern zusätzlich den
  Field-Read und `ItemStack#getHoverName()` ab. Falls die Reparatur nicht möglich ist,
  wird der Screen über `Minecraft.getInstance().setScreen(null)` geschlossen und der
  Hinweis „[CreachReportFix] Fehlerhaftes Werkbank-Rezept im Modpack abgefangen –
  Crash verhindert." im Chat ausgegeben.

### Hinzugefügt

* `CrashAnalyzer`-Regel für `WorkbenchScreen.m_7286_` / `"currentItem" is null`: benennt
  MrCrayfish's Gun Mod (`cgm`) sowie geänderte/entfernte Werkbank-Rezepte bzw. Addons
  im Modpack als Ursache.
* Die Mod-Version steht jetzt in `gradle.properties`; `./gradlew build` erzeugt direkt
  `creach-report-fix-1.3.0-client.jar`.

## [1.2.0] – 2026-11-17

Diese Version behebt die beiden häufigsten Crash-Ursachen aus den eingesandten Reports
endgültig. Der entscheidende Fehler der Vorgänger war, dass nur *ein* Mixin registriert war
und die ModelBakery-/Gun-Mod-Guards dadurch nie aktiv wurden.

### Behoben

* `ConcurrentModificationException` in `ModelBakery.<init>` beim Iterieren des
  Item-Registry-KeySets (45 von 58 Reports). `ModelBakeryMixin` iteriert jetzt eine stabile
  Kopie; `RegistrySnapshots` erstellt sie mit bis zu 40 Versuchen und Backoff
  (`LockSupport.parkNanos`), falls die Registry gerade umgebaut wird.
* `ConcurrentModificationException` in MrCrayfish's Gun Mod (`MetaLoader.getResourceSuppliers`,
  6 Reports). `CgmMetaLoaderMixin` leitet `IForgeRegistry.getValues()` auf einen Snapshot um.
* `NullPointerException` in FramedBlocks, wenn `baseModel` noch nicht gesetzt ist. Die Mixins
  greifen jetzt über `support/FieldLookup` (Reflection) zu, statt Felder per `@Shadow` zu
  deklarieren – dadurch startet der Mod auch ohne FramedBlocks.
* Hänger/Standbild durch das Lade-Overlay bei Server-Resource-Packs
  (`LoadingOverlayMixin`, SRG-Felder `f_96163_`–`f_96165_`).
* Debug-Tasten-Abfang (`KeyboardHandlerMixin`) korrigiert auf `m_90932_`.

### Hinzugefügt

* **Crash Assistant**: Fenster nach einem Crash mit Beschreibung, Fehlerzeile, erkannten
  Problemen, Lösungshinweisen und verdächtigen Mods. Erscheint beim nächsten Start.
* `CrashAnalyzer` mit Regeln für Registry-/Reload-Konflikte, Litematica/WorldSchematic,
  Embeddium-Flüssigkeiten, Block-Update-Konflikte, RAM, fehlende Abhängigkeiten,
  Mixin-Konflikte, Grafiktreiber und doppelte Mods.
* Konfiguration `config/creachreportfix-client.properties`
  (`crash-screen-enabled`, `crash-report-max-age-days`).
* Deutsche Doku, `CHANGELOG.md`, Prüfsumme zur JAR.

### Geändert

* `creachreportfix.mixins.json` registriert jetzt **alle** sieben Client-Mixins.
* Projektstruktur aufgeräumt: Quelltext unter `src/main/...`, fertige JARs unter `releases/`.
* `ClientPacketListenerMixin` bleibt bewusst unregistriert (Kick-Gefahr auf Servern).

### Entfernt

* Alte Sammelordner-Struktur (`cooldown deteckt/` inklusive Build-Artefakten).
* Veraltete `creach-report-fix-1.1.1-client.jar` aus `releases/`.

## [1.1.3] und älter

Nicht mehr dokumentiert. Die JARs liegen unter `releases/legacy/` und sind **nicht** zu
verwenden – dort fehlen die meisten Fixes, weil die Mixins nicht registriert waren.

[1.3.1]: https://github.com/zwerg061/Wolf-crashes/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/zwerg061/Wolf-crashes/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/zwerg061/Wolf-crashes/compare/c68ed3d...48cfcdb
