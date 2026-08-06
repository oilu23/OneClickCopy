# Release process

## Signing

Release builds are signed with the keystore described in `keystore.properties`
at the repository root. **That file and the keystore itself are gitignored and
must never be committed.**

`keystore.properties` format:

```properties
storeFile=/absolute/path/to/oneclickcopy-release.jks
storePassword=<password>
keyAlias=oneclickcopy
keyPassword=<password>
```

If the file is absent the project still builds; the release APK is simply
unsigned. That keeps CI and fresh clones working.

## ⚠️ Protect the keystore

The signing key **is** the app's identity. Android will not install an update
signed by a different key, and Google Play permanently binds a listing to its
upload key.

**If the keystore is lost, the app can never be updated again.** The only
recovery is publishing under a new package name, which orphans every existing
install.

Back it up now, in at least two places that are not this machine:

- an encrypted password manager entry (attach the `.jks`, store the password)
- an encrypted archive on separate physical media
- a private, encrypted cloud location

Store the password **separately** from the keystore file.

Current key details:

| Field | Value |
| --- | --- |
| Algorithm | RSA 4096 |
| Alias | `oneclickcopy` |
| Valid until | 2056 |
| SHA-1 | `E1:14:A5:3F:57:97:71:62:44:0B:12:82:2D:22:43:25:12:7A:94:7F` |

## Google Cloud Console

The signing certificate must be registered or Google sign-in fails with
`code 10` (`DEVELOPER_ERROR`).

At https://console.cloud.google.com/apis/credentials, create an **Android**
OAuth client with:

- Package name: `com.oneclickcopy`
- SHA-1: the fingerprint above

Multiple Android clients may coexist, so a debug or CI key can be registered
alongside the release key.

The **Web** client ID in `app/src/main/res/values/config.xml` is what
`requestIdToken()` consumes. It is not a secret — Android apps are public
clients — but deleting that client in the console breaks sign-in for every user.

## Cutting a release

```bash
# 1. bump versionCode and versionName in app/build.gradle.kts
# 2. verify everything from clean
./gradlew clean testDebugUnitTest lintDebug assembleRelease

# 3. confirm the APK is signed with the expected key
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk

# 4. tag and publish
git tag -a v2.1 -m "v2.1"
git push origin v2.1
gh release create v2.1 app/build/outputs/apk/release/app-release.apk
```

`versionCode` must increase on every release or Android refuses the update.

## Verifying an APK before distribution

```bash
apksigner verify --print-certs app-release.apk   # expect the SHA-1 above
aapt dump badging app-release.apk | head -1      # expect com.oneclickcopy
```
