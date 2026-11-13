# Galaxy S10 : connaître la version tzdata avant/après redémarrage

## Tâche pour Claude Code

Générer un projet Android complet et minimal (Kotlin) + un workflow GitHub Actions qui build l'APK debug, afin d'afficher la version tzdata d'un Galaxy S10 (Android 12). Détails dans la conversation ci-dessous.

Exigences :
- Projet complet : `settings.gradle.kts`, `build.gradle.kts` racine, module `app` (`compileSdk`, `minSdk = 24`, `targetSdk = 31`), `AndroidManifest.xml` (activité `MAIN`/`LAUNCHER`, aucune permission), Gradle wrapper commité (`gradlew` + `gradle/wrapper/`).
- Activité unique affichant : `android.icu.util.TimeZone.getTZDataVersion()`, le contenu de `/apex/com.android.tzdata/etc/tz/tz_version` (avec `runCatching`, la lecture peut être bloquée par SELinux), et `java.util.TimeZone.getDefault().id`.
- Workflow `.github/workflows/build.yml` : `ubuntu-latest`, `actions/setup-java`, `./gradlew assembleDebug`, puis `actions/upload-artifact` sur `app/build/outputs/apk/debug/app-debug.apk`.
- APK debug (clé debug auto-générée), installable directement.

---

## Conversation

### Utilisateur

J'ai un Galaxy S10, il m'indique qu'il faut que je redémarre mon téléphone pour appliquer les timezones. Je connais bien les timezones et sa tzdata db. Comment savoir quelle version j'ai avant et après redémarrage ?

### Claude

**Version actuellement active (avant et après reboot)**

Sur Android 10+, le tzdata est un module APEX (`com.android.tzdata`). Le module actif est monté sur `/apex/com.android.tzdata`. Via adb (pas besoin de root) :

```sh
adb shell cat /apex/com.android.tzdata/etc/tz/tz_version
# format: <format>.<IANA version>.<revision>, ex: 004.2025b.001
```

Compare la sortie avant et après redémarrage. Avant, tu vois l'ancienne version, car la mise à jour n'est que *staged*. Après, tu vois la nouvelle.

**Version de l'APEX (versionCode)**

```sh
adb shell pm list packages --apex-only --show-versioncode | grep -i tzdata
```

**Update en attente (staged)**

```sh
adb shell pm get-stagedsessions
```

Tu y verras la session APEX avec `isStaged=true`, `isSessionReady=true` et le package `com.google.android.tzdata*`. Elle sera activée au prochain boot.

**Sans adb**

Depuis une app ou un shell, `android.icu.util.TimeZone.getTZDataVersion()` donne la version ICU. Elle est généralement alignée sur le tzdata, mais pas toujours (ICU a son propre cycle sur certains builds). `tz_version` reste la référence.

**Si l'APEX n'est pas là (ROM ancienne)**

Sur Android 9 et antérieur, la version est dans `/system/usr/share/zoneinfo/tzdata` (en-tête `tzdata2018e`). Les mises à jour OTA vont dans `/data/misc/zoneinfo/current/`, lisible seulement en root. Le S10 a normalement reçu Android 12, donc c'est le cas APEX qui s'applique.

---

### Utilisateur

Je n'ai pas adb, quelle app ou shell peut m'aider à avoir cette info ?

### Claude

**Termux** (via F-Droid) est le plus simple. Les apps lisent déjà `/apex/com.android.tzdata/...` (libcore y charge le tzdata), donc le fichier est normalement lisible sans root :

```sh
cat /apex/com.android.tzdata/etc/tz/tz_version
```

Lance-le avant et après le reboot et compare.

Pour le versionCode de l'APEX, essaie :

```sh
pm list packages --apex-only --show-versioncode | grep -i tzdata
```

Ça marche souvent depuis Termux, mais ce n'est pas garanti selon la ROM Samsung.

`pm get-stagedsessions` (update en attente) nécessite les droits shell, donc il ne fonctionnera pas dans Termux seul. Deux options :
- **Shizuku** (démarrage par Wi-Fi debugging, sans PC) + Termux : tu obtiens un shell avec les droits adb.
- **Débogage sans fil** (Android 11+) : adb depuis Termux avec `pkg install android-tools`, puis `adb pair` / `adb connect` sur localhost.

Si `tz_version` est illisible (SELinux Samsung), une app avec Java peut afficher `android.icu.util.TimeZone.getTZDataVersion()`. Je n'ai pas d'app précise à te recommander pour ça. Le plus fiable reste Termux + `tz_version`.

---

### Utilisateur

Tu peux chercher une app qui fasse ça ?

### Claude

Je n'ai pas trouvé d'app grand public dédiée à l'affichage de la version tzdata. Les résultats ne remontent que de la doc AOSP et des outils d'update qui demandent le root (comme TZUpdater), qui ne servent pas ici.

Pour ton cas, deux pistes :

1. **Termux + `cat /apex/com.android.tzdata/etc/tz/tz_version`**. C'est le plus rapide. Sur Android 12 (dernier OS du S10), le package de l'APEX est `com.google.android.tzdata3`, donc le `grep -i tzdata` de `pm list packages --apex-only --show-versioncode` fonctionne aussi.

2. **Une mini-app à toi**. L'API `android.util.TimeUtils.getTimeZoneDatabaseVersion()` est masquée (`@hide`). Utilise plutôt `android.icu.util.TimeZone.getTZDataVersion()`, publique depuis l'API 24, qui renvoie par exemple `2025b`. Une activité d'une ligne suffit à l'afficher.

Si ce que tu cherches est un indicateur « update en attente » avant reboot, aucun des deux ne le donne : ils lisent la version active, pas celle qui est staged. Pour la version staged, il faut passer par `pm get-stagedsessions` (droits shell, donc Shizuku ou adb sans fil depuis Termux).

Sources :
- [Time Zone Data module (AOSP)](https://source.android.com/devices/architecture/modular-system/timezone)
- [TimeUtils.getTimeZoneDatabaseVersion](https://scriptagc.wasmer.app/http_developer_android_com/reference/android/util/TimeUtils)

---

### Utilisateur

Moyen simple de faire une app qui lise depuis l'API la version ?

### Claude

Une activité unique qui affiche la version ICU et lit `tz_version` :

```kotlin
// MainActivity.kt
package dev.tzv

import android.app.Activity
import android.icu.util.TimeZone
import android.os.Bundle
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val apex = runCatching {
            File("/apex/com.android.tzdata/etc/tz/tz_version").readText().trim()
        }.getOrElse { "n/a: $it" }
        setContentView(TextView(this).apply {
            textSize = 20f
            setPadding(48, 96, 48, 48)
            text = "ICU tzdata: ${TimeZone.getTZDataVersion()}\n" +
                   "tz_version: $apex\n" +
                   "java.util.TimeZone: ${java.util.TimeZone.getDefault().id}"
        })
    }
}
```

Le `build.gradle.kts` de module reste minimal : `minSdk = 24`, `targetSdk = 31`, plugin `com.android.application` et `org.jetbrains.kotlin.android`. Le manifest ne déclare que l'activité en `MAIN`/`LAUNCHER`, sans permission.

**Pour builder sans Android Studio :**
- **Rapide** : GitHub Actions avec `actions/setup-java` + `./gradlew assembleDebug`, puis téléchargement de l'APK debug (signé automatiquement avec la clé debug, donc installable tel quel).
- **Local** : `sdkmanager` + `gradle assembleDebug`, puis `adb install` (ou transfert de l'APK et installation manuelle).

À savoir : `getTZDataVersion()` renvoie la version ICU (`2025b`), alors que `tz_version` donne le triplet `format.version.revision`. Sur certains builds Samsung, SELinux peut bloquer la lecture de `tz_version` ; l'appel ICU fonctionne dans tous les cas. Pour comparer avant et après le reboot, relance simplement l'app.

---

### Utilisateur

Tu dis que je peux faire le build dans une github action, télécharger l'apk et l'installer, et ça va marcher ?

### Claude

Oui, ça marche en pratique, mais je n'ai pas testé ce projet précis, et mon message précédent omettait des pièces :

- **Projet complet requis** : il faut `settings.gradle.kts`, le `build.gradle.kts` racine, le module `app` avec `compileSdk`, `AndroidManifest.xml`, et le Gradle wrapper (`gradlew` + `gradle/wrapper/`) commité. Sinon, utilise `gradle/actions/setup-gradle` et lance `gradle assembleDebug`.
- **SDK Android** : préinstallé sur les runners `ubuntu-latest`, rien à ajouter.
- **Signature** : `assembleDebug` signe avec une clé debug générée à la volée, donc l'APK est installable. Il sera simplement signé avec une clé jetable, ce qui n'a aucune importance ici.
- **Artefact** : `actions/upload-artifact` livre un zip contenant `app/build/outputs/apk/debug/app-debug.apk`. Il faut le télécharger depuis l'onglet Actions (connecté à GitHub) puis le dézipper sur le téléphone.
- **Installation** : active « Installer des apps inconnues » pour le navigateur ou le gestionnaire de fichiers utilisé. Play Protect peut afficher un avertissement sur une app inconnue ; tu peux passer outre.
- **Compatibilité** : un `targetSdk` de 31 passe sans souci sur Android 12.

Si tu veux, je génère le projet complet (fichiers Gradle, manifest, activité, workflow YAML) prêt à pousser.

---

### Utilisateur

je vais demander à un claude code local de me le faire, tu peux me mettre toute cette conversation dans un markdown ?
