# Contributing

Thank you for helping build OpenOnion Messages.

Before opening a pull request:

1. discuss protocol, permission, or cryptography changes in an issue;
2. keep SMS content, credentials, and private customer data out of tests;
3. add regression tests for changed behavior;
4. run `./gradlew test lint assembleDebug`; and
5. explain security and privacy effects in the pull request description.

Use four-space indentation for Kotlin continuation blocks, follow standard
Kotlin naming conventions, and write comments that explain decisions rather
than restating the code.

GitHub issue forms require synthetic reproduction data and route suspected
vulnerabilities to the private security policy. Pull requests must record
security/privacy effects, compatibility requirements, and the verification
scope. Dependabot checks Gradle and GitHub Actions dependencies monthly;
security fixes may be opened sooner when GitHub detects a vulnerable version.
