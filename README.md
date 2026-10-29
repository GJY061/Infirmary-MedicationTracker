# Infirmary Medication Tracker

An Android demonstration app built with Kotlin and Jetpack Compose. Features include medication and symptom tracking, daily taken status, symptom charts, drug-label lookup, and generated encouragement tips.

The app uses MVVM, Room, Retrofit with Gson, coroutines, and StateFlow. Its application ID is `com.example.infirmary`.

## Run locally

Open the project in Android Studio, install the requested Android SDK, and run the app. The Gradle wrapper uses a Java 21 toolchain.

The checked-in `secrets.defaults.properties` contains a blank key so the project can build without private configuration. Gemini features are optional. Add your own key to an untracked root `local.properties` file:

```properties
GEMINI_API_KEY=YOUR_OWN_GEMINI_API_KEY
```

Rebuild after changing the key. Do not commit this file or distribute an APK containing a private service key.

## Demonstration data

Bundled CSV records are display examples, not prescriptions or medical advice. Patient names are generic demonstration labels, contact numbers are reserved fictional numbers, and initial passwords are blank. Free-text notes contain demonstration descriptions.

Demo phone numbers come from the [ACMA list reserved for fictional works](https://www.acma.gov.au/phone-numbers-use-tv-shows-films-and-creative-works).

To try a seeded account, choose **Claim Account**, use patient ID `P1001` and demo phone `0491570006`, then choose your own local demo password. Accounts are listed in `app/src/main/assets/patients.csv`.

The clinician dashboard uses the public demonstration access code `DEMO_ONLY_ACCESS`.

## Prototype limitations

Authentication and access control are local demonstrations. Passwords are stored in plain text, and an API key supplied through BuildConfig can be extracted from a built APK. This is not a production authentication system.

Drug lookup calls openFDA. Generating a tip sends the selected demo patient's context to Gemini. The app is for demonstration and tracking and does not replace professional medical advice.
