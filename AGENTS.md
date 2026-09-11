# Repository agent instructions

@RULES.md

- Before planning, editing, generating, or reviewing any Android interface work, read and follow `RULES.md`.
- Declare whether the work targets mobile, TV, playback, or shared UI infrastructure and apply only the matching platform rules.
- Cite applicable rule IDs in plans, reviews, and verification notes. Do not treat existing code as an exception to the rules.
- Before changing versions, preparing artifacts, publishing, promoting, or withdrawing a release, read `RULES.md` and `docs/DEVELOPMENT_AND_RELEASE.md`; cite the applicable `REL-*` and `QA-*` rules in release notes and verification.
- A release is complete only after the locally signed GitHub artifact, checksum, build report, signed metadata feed, public download, and website state agree. GitHub Actions must not build, sign, or publish production APKs.
