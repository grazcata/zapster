# Zapster ⚡📺

Application Android moderne et minimaliste de télécommande pour Android TV et Google TV (Sony Bravia, TCL, Xiaomi, Nvidia Shield, Philips, Chromecast avec Google TV, etc.).

Inspirée des meilleures applications de télécommande (comme Remote ATV de Simha), mais **100 % propre, sans publicité, sans traceur et sans abonnement**.

---

## 🌟 Pourquoi Zapster fonctionne là où les autres échouent ?

La plupart des télécommandes bricolées tentent de passer par ADB (port 5555) qui nécessite le débogage USB activé et une clé RSA, ou par l'API Sony IP Control (port 80) qui est verrouillée par défaut avec un `403 Forbidden` sur les firmwares récents.

**Zapster utilise le protocole officiel Android TV Remote v2** (ports `6466` / `6467`) avec chiffrement TLS et échange de certificats.
- **Aucune option développeur requise sur la TV**.
- **Aucun paramètre à modifier sur la TV**.
- **Appairage sécurisé en 5 secondes** : la TV affiche un code à 6 caractères sur l'écran, vous le saisissez sur le téléphone, et la TV est enregistrée de manière permanente.

---

## 🚀 Fonctionnalités

- **Navigation D-Pad fluide & réactive** :
  - Pavé directionnel (Haut, Bas, Gauche, Droite, bouton central **OK**).
  - Touches système natives : **Accueil (Home)**, **Retour**, **Alimentation (Power)**, **Sources (Inputs HDMI/Tuner)**.
- **Gestion audio complète** :
  - **Volume +**, **Volume −**, **Mute**.
- **Contrôles multimédias** :
  - **Lecture / Pause**, **Retour rapide**, **Avance rapide**.
- **Détection automatique sur le réseau** :
  - Découverte automatique des téléviseurs Android TV / Google TV sur le Wi-Fi (SSDP / mDNS) ou saisie directe de l'adresse IP.
- **Design moderne Material 3** :
  - Interface sombre et soignée avec Jetpack Compose.
  - Retour haptique tactile à chaque appui.
- **100 % Respectueux de la vie privée** :
  - Zéro publicité (aucun SDK AdMob, Facebook, etc.).
  - Zéro télémétrie ou tracking.
  - Connexion directe et locale en Wi-Fi.

---

## 📥 Installation

1. Téléchargez le fichier **[Zapster-v1.0.0.apk](https://github.com/grazcata/zapster/releases/latest)** depuis la section Releases.
2. Installez l'APK sur votre smartphone Android (Android 8.0 Oreo ou version supérieure).
3. Assurez-vous que votre téléphone est connecté au même réseau Wi-Fi que la TV.
4. Lancez **Zapster** : l'application détecte votre téléviseur. Cliquez sur **Pair with TV**.
5. Recopiez le code à 6 chiffres/lettres affiché sur votre écran de télévision.
6. La télécommande est prête et active !

---

## 🛠️ Compilation depuis les sources

Pré-requis : JDK 17 et Android SDK (Platform 34).

```bash
git clone https://github.com/grazcata/zapster.git
cd zapster
./gradlew assembleRelease
```

L'APK signé sera généré dans `app/build/outputs/apk/release/app-release.apk`.

---

## 📄 Licence

Licence MIT.
