plugins {
    id("com.android.asset-pack")
}

/**
 * Install-time asset pack carrying the bundled STT and TTS models.
 *
 * Why this module exists: the offline constraint requires all six models to be present
 * on the device at first launch, which puts the package well above the 150 MB limit for
 * a plain APK. An Android App Bundle with an **install-time** asset pack raises that
 * ceiling while keeping the models present before the app first runs -- so nothing is
 * ever fetched at runtime and the zero-network guarantee is untouched.
 *
 * Install-time packs are served through the ordinary AssetManager, so no code changes
 * are needed: `context.assets.open("models/...")` resolves exactly as it would from a
 * plain APK.
 *
 * Do NOT change deliveryType to fast-follow or on-demand. Both fetch over the network
 * after install, which would break the central constraint of this project.
 */
assetPack {
    packName.set("itantra_models")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
