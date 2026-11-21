# Releases

Hier liegen die fertigen, spielbaren Mod-JARs.

| Datei | Größe | Verwendung |
| --- | --- | --- |
| **`creach-report-fix-1.3.1-client.jar`** | 47 702 B | **Diese Datei installieren.** Werkbank-Guard jetzt ohne Screen-Close. |
| `creach-report-fix-1.3.1-client.jar.sha256` | – | Prüfsumme zur Kontrolle des Downloads. |
| `legacy/wolf-crash-fix-legacy.jar` | 12 440 B | Sehr alte Version, **nur als Referenz**. Dort sind die meisten Fixes nicht registriert. |

## Installation

1. Minecraft Forge **1.19.2** (43.x) installieren.
2. Alte Versionen aus `.minecraft/mods` löschen (z. B. `creach-report-fix-1.3.0-client.jar`).
3. `creach-report-fix-1.3.1-client.jar` nach `.minecraft/mods` kopieren.
4. Fertig – der Mod ist rein clientseitig.

## Download prüfen (optional)

PowerShell:

```powershell
$jar = "$env:APPDATA\.minecraft\mods\creach-report-fix-1.3.1-client.jar"
(Get-FileHash $jar -Algorithm SHA256).Hash
```

Der Wert muss mit dem Inhalt von `creach-report-fix-1.3.1-client.jar.sha256` übereinstimmen.

## Was 1.3.1 behebt

* 1.3.0 schloss die Werkbank-GUI nach dem Abfangen des Crashes sofort wieder. 1.3.1
  bereinigt stattdessen die defekten Rezepte in `tabs` → `Tab.items`, setzt ein fehlendes
  Ergebnis auf das erste gültige Rezept-Item (oder `ItemStack.EMPTY`) und lässt die GUI
  offen. `setScreen(null)` gibt es nur noch als Notanker bei unerwarteter Exception.

## Was 1.3.0 behebt

* `NullPointerException` in der CGM-Waffen-Werkbank (`WorkbenchScreen.m_7286_` /
  `m_6305_`, `currentItem` = null) durch fehlende oder geänderte Rezepte im Modpack:
  `CgmWorkbenchScreenMixin` setzt das Ergebnis auf `ItemStack.EMPTY` (in 1.3.0 wurde
  der Screen dabei noch geschlossen – seit 1.3.1 bleibt er offen).
* Der `CrashAnalyzer` benennt diesen Fall jetzt als MrCrayfish's Gun Mod (`cgm`) bzw.
  modifizierte Werkbank-Rezepte im Modpack.

## Was 1.2.0 behebt

* `ConcurrentModificationException` in `ModelBakery.<init>` – die Item-Registry wird jetzt als
  stabile Kopie iteriert (häufigster Crash in den eingesandten Reports).
* `ConcurrentModificationException` in MrCrayfish's Gun Mod beim Meta-Laden.
* `NullPointerException` in FramedBlocks, wenn `baseModel` noch nicht gesetzt ist.
* Hänger/Standbild durch das Lade-Overlay bei Server-Resource-Packs.
* Absturz beim manuellen Ressourcen-Reload (F3+T / F3+S).

## Neu in 1.2.0 (bereits enthalten)

* **Crash Assistant:** Nach einem Crash erscheint beim nächsten Start ein Fenster mit
  Beschreibung, erkannten Problemen, Lösungshinweisen und verdächtigen Mods.
* Optionen in `config/creachreportfix-client.properties`.

## Selbst bauen

```bash
gradlew.bat build
```

Das Ergebnis liegt danach unter `../build/libs/`.
