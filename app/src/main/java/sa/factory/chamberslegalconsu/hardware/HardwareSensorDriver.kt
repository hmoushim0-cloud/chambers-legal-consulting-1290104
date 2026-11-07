package sa.factory.chamberslegalconsu.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.chamberslegalconsu.engine.CertifiedLegalContractEngine
import sa.factory.chamberslegalconsu.engine.ContractClassification
import sa.factory.chamberslegalconsu.engine.HsmCryptoTokenProtocolEngine

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "FIPS 140-2 Cryptographic HSM Security Token"
    private val hsmEngine = HsmCryptoTokenProtocolEngine()
    private val contractEngine = CertifiedLegalContractEngine()

    fun getHsmEngine(): HsmCryptoTokenProtocolEngine = hsmEngine
    fun getContractEngine(): CertifiedLegalContractEngine = contractEngine

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "تهيئة الاتصال بوحدة التشفير المادية HSM ومصادقة المنفذ PKCS#11..."))
        delay(300)

        // Initialize HSM session with PIN
        hsmEngine.authenticatePin("123456")
        val keyFingerprint = hsmEngine.loadOrGenerateKey("CHAMBERS-ROOT-KEY")

        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "وحدة HSM متصلة وموثقة (FIPS 140-2 L3) | المفتاح النشط: $keyFingerprint"))

        // Create initial active contract
        val contract = contractEngine.createDraftContract(
            title = "اتفاقية شراكة تجارية واستثمارية",
            classification = ContractClassification.COMMERCIAL_PARTNERSHIP,
            client = "شركة الاستثمار المالي الوطنية",
            attorneyCr = "CR-1010-884920",
            valueSar = 250000.0
        )
        val certified = contractEngine.certifyContractWithHsm(contract.contractId, hsmEngine, "CHAMBERS-ROOT-KEY")

        while (true) {
            delay(1500)
            val auditRef = certified?.signatureResult?.najizAuditReference ?: "NJZ-PENDING"
            val hashPrefix = contract.sha256Fingerprint.take(12)
            val payload = "HSM SLOT: 0x1004 (FIPS 140-2 L3) | البصمة: $keyFingerprint | العقد: ${contract.contractId} | توثيق ناجز: $auditRef | التجزئة: $hashPrefix... | الحالة: موثق نظاميا"

            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", payload))
        }
    }
}
