# Macrobenchmark

This com.android.test module measures the app's credential-free cold startup path.

    ./gradlew :macrobenchmark:connectedCheck

Macrobenchmarks need a connected physical device or a profileable emulator. For trustworthy
numbers, use a non-debuggable benchmark/release-like app build on the target device; this starter
module does not create signing keys, contact the network, or require account credentials.
