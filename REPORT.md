# Rapport : version effective de tzdata sur un Galaxy S10 (Android 12)

Contexte : le téléphone demandait un redémarrage pour appliquer une mise à jour des fuseaux horaires. Une petite app (ce dépôt) a été écrite pour lire les versions avant/après. Constats obtenus sur l'appareil, puis interprétation.

## Rappel : rôle des composants

- **APEX** : conteneur de mise à jour de composants système (Mainline, depuis Android 10), mis à jour via Google Play ou OTA fabricant. Version d'usine dans `/system/apex`, mise à jour dans `/data/apex`, active après redémarrage, montée sur `/apex/<nom>`.
- **`com.android.tzdata`** (APEX) :
  - `etc/tz/tzdata` : TZif concaténés + index.
  - `etc/tz/tz_version` : format `NNN.NNN|AAAAx|NNN` (version majeure du format, mineure, version IANA, révision Android).
  - `tzlookup.xml`, `telephonylookup.xml`.
  - `etc/icu/icu_tzdata.dat` : règles au format ICU.
- **ICU / `com.android.i18n`** : bibliothèque d'internationalisation (`android.icu.*`). Données complètes dans `/apex/com.android.i18n/etc/icu/icudt68l.dat` (ICU 68 sur Android 12), dont les noms localisés des zones.
- **Qui lit quoi** (d'après le code AOSP) :
  - `java.util.TimeZone` lit `tzdata`.
  - `java.time` passe par les règles ICU.
  - `android.icu.util.TimeZone.getTZDataVersion()` donne la version ICU.
- **Ordre de recherche** : `/data/misc/zoneinfo/current/` (overlay) avant l'APEX tzdata, puis (pour ICU) l'APEX i18n. Un overlay présent masque donc l'APEX.

## Certain (observé sur le téléphone)

- ICU : 2026c avant le redémarrage, 2026f après.
- `tz_version` : `005.001 | 2022f | 001`, inchangé par le redémarrage.
- En-tête du fichier `tzdata` de l'APEX : `tzdata2022f`.
- Versions des modules (`apex_manifest.pb`) : `com.android.tzdata` = 319999900, `com.android.i18n` = 1.
- `/data/misc/zoneinfo/current/tzdata` : `exists=true`, `canRead=true`.
- `icu_tzdata.dat` de l'APEX : 232688 octets ; i18n : `icudt68l.dat`.
- Décalages au 2025-07-15T12:00Z : `Africa/Cairo` +03:00 et `America/Asuncion` -03:00, identiques dans `java.util.TimeZone`, `android.icu` et `java.time`.
- `/apex/apex-info-list.xml` et le listing de `/apex` : EACCES pour une app normale.
- Le build GitHub Actions (ubuntu-24.04, actions à jour) passe sans warning.

## Probable

- Les trois APIs utilisent des règles bien plus récentes que 2022f (dépend de l'historique tz : DST égyptien rétabli en 2023, Paraguay en UTC-3 permanent en 2024 ; de mémoire, non vérifié).
- Les règles 2026f sont actives ; `tz_version` ne les reflète pas (il décrit l'APEX d'usine).
- L'overlay dans `/data/misc/zoneinfo/current/` est la source des données récentes (ICU et `java.util.TimeZone` le lisent avant l'APEX).
- Le redémarrage a appliqué l'overlay (2026c -> 2026f).
- `java.time` passe par les règles ICU (code Android 12, non mesuré).
- Les APEX tzdata/i18n sont probablement des versions d'usine non mises à jour par Mainline (déduit des numéros 319999900 et 1).

## Non établi

- Version exacte des données de `java.util.TimeZone` (seulement : au moins celle qui contient le DST égyptien et le Paraguay en UTC-3).
- Qui livre l'overlay : ancien mécanisme d'APK, Samsung ou Google Play.
- Que l'overlay soit réellement lisible à l'ouverture (`canRead` vrai ne garantit pas que SELinux autorise la lecture).
- Pourquoi `PackageManager` (`MATCH_APEX`) a répondu « not visible » alors que les sources AOSP indiquent que les APEX ne sont pas filtrés.

## Corrections apportées en cours d'analyse

- Les données ICU ne viennent pas (d'abord) du module i18n : l'overlay passe en premier.
- `java.util.TimeZone` ne tourne probablement pas sur 2022f.
- `tz_version` n'est pas la référence ici : c'est la version ICU.

## Sources

- https://source.android.com/docs/core/ota/modular-system/timezone
- https://source.android.com/docs/core/permissions/timezone-rules
- https://source.android.com/docs/core/ota/apex
- https://android.googlesource.com/platform/system/timezone/+/refs/heads/main/README.android
- https://android.googlesource.com/platform/libcore/+/refs/heads/android12-release/ojluni/src/main/java/java/time/zone/IcuZoneRulesProvider.java
