# Fossify Phone
<img alt="Logo" src="graphics/icon.webp" width="120" />

<a href='https://play.google.com/store/apps/details?id=org.fossify.phone'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height=80/></a> <a href="https://f-droid.org/packages/org.fossify.phone/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" height=80/></a> <a href="https://apt.izzysoft.de/fdroid/index/apk/org.fossify.phone"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height=80/></a>

Empower your calls, and safeguard your data. Fossify Phone redefines the mobile app experience with unmatched privacy and efficiency. Free from ads and intrusive permissions, it's designed for seamless and secure everyday communication.

📱 **YOUR PRIVACY, OUR PRIORITY:**  
Welcome to the Fossify Phone App, where your digital privacy is paramount. Switch to a mobile experience that respects your data, ensuring your personal information remains secure and private.

🚀 **SEAMLESS PERFORMANCE:**  
The Fossify Phone App offers a fluid and responsive mobile interface, enhancing your phone's performance while safeguarding your privacy. Experience a lag-free, smooth user experience, optimized for efficiency and speed.

🌐 **OPEN-SOURCE ASSURANCE:**  
With the Fossify Phone App, transparency is at your fingertips. Built on an open-source foundation, our app allows you to review our code on GitHub, fostering trust and a community committed to privacy.

🖼️ **TAILOR-MADE CUSTOMIZATION:**  
Customize your mobile experience with the Fossify Phone App. Adjust your app settings for a personalized interface, from thematic designs to functional preferences. Enjoy a user interface that's intuitive and uniquely yours.

🔋 **EFFICIENT RESOURCE MANAGEMENT:**  
The Fossify Phone App is designed for optimal resource usage, contributing to extended battery life. It's light on your phone's resources, ensuring your device runs efficiently with minimized battery drain.

Download the Fossify Phone App now and step into a mobile world where privacy seamlessly blends with functionality. Your journey towards a safer, personalized mobile experience starts here.

➡️ Explore more Fossify apps: https://www.fossify.org<br>
➡️ Open-Source Code: https://www.github.com/FossifyOrg<br>
➡️ Join the community on Reddit: https://www.reddit.com/r/Fossify<br>
➡️ Connect on Telegram: https://t.me/Fossify

<div align="center">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="30%">
</div>

---

## Building a Signed APK

To build and release a signed APK via GitHub Actions, you need to configure the following repository secrets:

### Required Secrets

| Secret Name | Description | How to Obtain |
|---|---|---|
| `SIGNING_STORE_FILE` | Base64-encoded keystore file (`.jks` or `.keystore`) | Generate with `keytool -genkeypair -v -keystore release.keystore -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000`, then encode: `base64 -w 0 release.keystore > keystore_base64.txt` |
| `SIGNING_STORE_PASSWORD` | Keystore password | Set when generating the keystore with `keytool` |
| `SIGNING_KEY_ALIAS` | Key alias name | Set with `-alias` flag when generating the keystore |
| `SIGNING_KEY_PASSWORD` | Key password | Set with `-keypass` when generating the keystore |
| `GH_TOKEN` | GitHub Personal Access Token (PAT) with `repo` scope | Generate at https://github.com/settings/tokens → Generate new token (classic) → Select `repo` scope |

### Key Generation Method

```bash
# 1. Generate keystore
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias my-key-alias \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# 2. Base64 encode the keystore for the GitHub secret
base64 -w 0 release.keystore > keystore_base64.txt

# 3. Copy the contents of keystore_base64.txt to the SIGNING_STORE_FILE secret
# 4. Set SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, and SIGNING_KEY_PASSWORD secrets
# 5. Generate a PAT at https://github.com/settings/tokens with repo scope for GH_TOKEN
```

### Setting Up Secrets in GitHub

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** for each secret listed above
4. Add the name and value for each secret
