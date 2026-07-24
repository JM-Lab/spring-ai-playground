# Code Signing Policy

This document describes how release artifacts of **Spring AI Playground**
(<https://github.com/spring-ai-community/spring-ai-playground>) are built, signed, and
published.

Free code signing provided by [SignPath.io](https://signpath.io), certificate by
[SignPath Foundation](https://signpath.org).

Spring AI Playground is an incubating open-source project of the Spring AI Community,
licensed under the Apache License 2.0. It is distributed free of charge as native desktop
installers for Windows, macOS, and Linux.

## What is signed and released

Official releases are the desktop installers produced for each tagged version:

- **Windows**: NSIS installer (`.exe`)
- **macOS**: disk image (`.dmg`)
- **Linux**: `.deb` and `.rpm` packages

The Windows installer is the artifact submitted for Authenticode code signing. macOS and
Linux packages are distributed through their platform-native mechanisms and are not part of
this Authenticode signing policy.

## Team

- **Committers and reviewers**: the maintainers listed in the repository.
- **Approvers** (authorized to approve signing requests): Jemin Huh (project lead).

All team members use multi-factor authentication for both SignPath and GitHub, and every
release is approved manually before it is signed.

## How artifacts are built and signed

1. A maintainer pushes a version tag to the official repository
   (`spring-ai-community/spring-ai-playground`).
2. The GitHub Actions release workflow (`.github/workflows/release.yml`) builds every
   installer from that exact commit, on GitHub-hosted runners, from public source only.
   Every signed binary carries the product name and version of the release.
3. The Windows installer is submitted to SignPath for Authenticode signing via the SignPath
   GitHub Action. Only artifacts produced by this CI run are submitted, and an approver
   confirms each signing request manually.
4. The signed installer and the other platform artifacts are published to the GitHub
   Releases page for that tag.

## Integrity and verification

Every release additionally ships:

- a SHA-256 checksum file (`.sha256`) for each installer, and
- a Sigstore SLSA build-provenance attestation.

Verification instructions (`shasum`, `Get-FileHash`, `gh attestation verify`) are
documented at
<https://spring-ai-community.github.io/spring-ai-playground/getting-started/#verify-your-download>.

## Privacy

Spring AI Playground sends anonymous usage telemetry (feature and event counts) to Google
Analytics by default, so the community can see which features are used. It transfers no
personal data and no user content. Telemetry can be turned off at any time by setting
`SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false` before launching. See
[Anonymous Usage Telemetry](https://spring-ai-community.github.io/spring-ai-playground/getting-started/#anonymous-usage-telemetry)
for exactly what is collected and how to opt out.

## Reporting

Suspected misuse of the signing certificate, or a compromised release, should be reported to
the maintainers through a GitHub private security advisory on the repository, or through the
repository's issue tracker.
