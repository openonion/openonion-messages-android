# Contributing

Thank you for helping build OpenOnion Messages.

By participating, you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).
Questions that are not bugs belong in [GitHub Discussions][discussions]; see
[SUPPORT.md](SUPPORT.md) for the complete routing policy.

## Development setup

Install JDK 17 and Android SDK 36, clone the repository, and run:

```bash
./gradlew test lint assembleDebug
```

Run `./gradlew connectedDebugAndroidTest` with an API 26+ emulator or device
when changing the database, Android Keystore, WorkManager delivery, SMS role,
or permission flows. All fixtures must be synthetic.

## Pull requests

Before opening a pull request:

1. discuss protocol, permission, or cryptography changes in an issue;
2. keep SMS content, credentials, and private customer data out of tests;
3. add regression tests for changed behavior;
4. update every affected document and protocol vector;
5. run `./gradlew test lint assembleDebug`; and
6. explain security, privacy, compatibility, and migration effects in the pull
   request description.

Use four-space indentation for Kotlin continuation blocks, follow standard
Kotlin naming conventions, and write comments that explain decisions rather
than restating the code.

GitHub issue forms require synthetic reproduction data and route suspected
vulnerabilities to the private security policy. Pull requests must record
security/privacy effects, compatibility requirements, and the verification
scope. Dependabot checks Gradle and GitHub Actions dependencies monthly;
security fixes may be opened sooner when GitHub detects a vulnerable version.

Contributions are licensed under Apache-2.0 under the terms described in
[LICENSE](LICENSE). Do not submit code or assets you do not have the right to
license. Maintainers may ask for provenance when generated or adapted material
is not obviously original.

[discussions]: https://github.com/openonion/openonion-messages-android/discussions
