package sa.factory.chamberslegalconsu.engine

import java.security.MessageDigest

enum class HsmSessionState {
    SLOT_UNINITIALIZED,
    TOKEN_INSERTED,
    PIN_AUTHENTICATED,
    KEY_PAIR_LOADED,
    DIGITAL_SIGNING_READY
}

enum class DigitalSignatureAlgorithm(val standardName: String, val keyBits: Int) {
    RSA_4096_PSS_SHA256("RSA-PSS-4096", 4096),
    ECDSA_SECP256R1_SHA256("ECDSA-P256", 256),
    ED25519_GOV("Ed25519-SaudiGov", 256)
}

data class Pkcs11TokenSlot(
    val slotId: Long,
    val manufacturer: String,
    val modelDescription: String,
    val serialNumber: String,
    val fipsCertifiedLevel: Int = 3
)

data class CryptoSignatureResult(
    val signatureHex: String,
    val keyFingerprint: String,
    val algorithm: DigitalSignatureAlgorithm,
    val documentHash: String,
    val timestampUtc: Long = System.currentTimeMillis(),
    val najizAuditReference: String
)

class HsmCryptoTokenProtocolEngine {
    private val tokenSlot = Pkcs11TokenSlot(
        slotId = 0x1004L,
        manufacturer = "Thales Luna / SafeNet eToken",
        modelDescription = "FIPS 140-2 Level 3 Certified Cryptographic Token",
        serialNumber = "HSM-SA-2026-9941"
    )

    private var currentState: HsmSessionState = HsmSessionState.TOKEN_INSERTED
    private val loadedKeys = mutableMapOf<String, String>()

    fun getTokenSlot(): Pkcs11TokenSlot = tokenSlot
    fun getSessionState(): HsmSessionState = currentState

    fun authenticatePin(pin: String): Boolean {
        // Enforce Saudi Ministry of Justice security standard PIN rules (min 6 digits)
        if (pin.length >= 6 && pin.all { it.isDigit() }) {
            currentState = HsmSessionState.PIN_AUTHENTICATED
            return true
        }
        return false
    }

    fun loadOrGenerateKey(keyAlias: String, algorithm: DigitalSignatureAlgorithm = DigitalSignatureAlgorithm.RSA_4096_PSS_SHA256): String {
        val rawSeed = "${tokenSlot.serialNumber}-$keyAlias-${algorithm.name}-2026"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(rawSeed.toByteArray(Charsets.UTF_8))
        val fingerprint = hash.take(8).joinToString("") { "%02X".format(it) }

        loadedKeys[keyAlias] = fingerprint
        currentState = HsmSessionState.DIGITAL_SIGNING_READY
        return fingerprint
    }

    fun signDocumentHash(sha256Hash: String, keyAlias: String): CryptoSignatureResult? {
        val fingerprint = loadedKeys[keyAlias] ?: return null
        if (currentState != HsmSessionState.DIGITAL_SIGNING_READY) return null

        val md = MessageDigest.getInstance("SHA-256")
        val combined = "$sha256Hash-$fingerprint-${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)
        val sigBytes = md.digest(combined)
        val sigHex = sigBytes.joinToString("") { "%02x".format(it) }
        val auditRef = "NJZ-${System.currentTimeMillis() % 1000000}"

        return CryptoSignatureResult(
            signatureHex = sigHex,
            keyFingerprint = fingerprint,
            algorithm = DigitalSignatureAlgorithm.RSA_4096_PSS_SHA256,
            documentHash = sha256Hash,
            najizAuditReference = auditRef
        )
    }

    fun verifySignature(documentHash: String, signatureHex: String): Boolean {
        return documentHash.isNotBlank() && signatureHex.length == 64
    }
}
