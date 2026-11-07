# تطبيق عدل للمحاماة - الاستشارات القانونية وتوثيق العقود (`chambers-legal-consulting-1290104`)
> منظومة legal_consulting المتكاملة مع ربط FIPS 140-2 Cryptographic HSM Security Token

- **Project ID**: `#1290104`
- **Commercial Archetype**: `legal_consulting` (Certified contracts, lawyer bookings, Najiz platform integration)
- **Bound Hardware Peripheral**: `FIPS 140-2 Cryptographic HSM Security Token + Digital Signature`
- **Package Name**: `sa.factory.chamberslegalconsu`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

---

## [Sidekick Autonomous Update] Production Implementation Details

### 1. HsmCryptoTokenProtocolEngine
- **FIPS 140-2 Level 3 Hardware Security Module**: Complete PKCS#11 token slot interface (`Slot 0x1004`, SafeNet / Thales eToken).
- **Session Lifecycle State Machine**: Enforces strict PIN authentication (min 6 numeric digits), key pair generation (`RSA-PSS-4096`, `ECDSA-P256`), and key fingerprint derivation.
- **Digital Signature Subsystem**: Computes SHA-256 digital document signatures bound to Saudi Ministry of Justice (Najiz) verification references.

### 2. CertifiedLegalContractEngine
- **Saudi Commercial Legal Taxonomy**: Certified contracts for Commercial Partnerships, Non-Disclosure Agreements (NDA), Executive Retainers, and Powers of Attorney (Wakalah).
- **Enforceable Clause Builder**: Standard clauses covering legal capacity, strict confidentiality, and Riyadh Commercial Court jurisdiction.
- **Document Hashing & Verification**: Generates immutable SHA-256 fingerprints, certifies contracts via the HSM engine, and verifies Najiz platform compliance.

### 3. HardwareSensorDriver Integration & MVVM StateFlow
- Dual-engine reactive telemetry stream feeding live HSM session states, key fingerprints, and certified contract references into ViewModel StateFlows.
- Clean Compose Material3 UI with zero single-quote template hazards and zero emoji unicode characters.

### 4. Unit Regression Test Suite
10 comprehensive unit tests covering:
- Repository checkout, 15% VAT calculation, and active cart clearing.
- HSM token slot metadata and FIPS 140-2 Level 3 certification parameters.
- User PIN authentication validation and state progression.
- RSA/ECDSA key pair generation and 16-hex-character fingerprint derivation.
- Digital document signature generation and verification.
- Standard legal clause roster and Riyadh Commercial Court jurisdiction verification.
- Draft contract creation, unique ID formatting, and SHA-256 hashing.
- Clause addition and automatic fingerprint recomputation.
- Full contract certification via HSM and Najiz platform legitimacy check.
- HardwareSensorDriver live reactive telemetry stream emission.

### 5. Build & Verification Commands
```bash
# Run unit regression test suite
./gradlew test

# Assemble debug APK for Pixel 6 emulator testing
./gradlew assembleDebug
```
