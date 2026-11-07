package sa.factory.chamberslegalconsu

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sa.factory.chamberslegalconsu.data.repository.ChambersLegalRepositoryImpl
import sa.factory.chamberslegalconsu.engine.CertifiedLegalContractEngine
import sa.factory.chamberslegalconsu.engine.ContractClassification
import sa.factory.chamberslegalconsu.engine.HsmCryptoTokenProtocolEngine
import sa.factory.chamberslegalconsu.engine.HsmSessionState
import sa.factory.chamberslegalconsu.engine.LegalClause
import sa.factory.chamberslegalconsu.hardware.HardwareSensorDriver
import sa.factory.chamberslegalconsu.model.ChambersLegalItem

class ChambersLegalRegressionTest {

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val sensorDriver = HardwareSensorDriver()
        val repository = ChambersLegalRepositoryImpl(sensorDriver)

        val item1 = ChambersLegalItem("t1", "خدمة توثيق عقد تجاري", "الوصف", 100.0, "معتمد", "عام")
        val item2 = ChambersLegalItem("t2", "خدمة استشارة قضائية معتمدة", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testHsmTokenSlotMetadata() {
        val hsm = HsmCryptoTokenProtocolEngine()
        val slot = hsm.getTokenSlot()

        assertEquals(0x1004L, slot.slotId)
        assertEquals(3, slot.fipsCertifiedLevel)
        assertTrue(slot.serialNumber.startsWith("HSM-SA-"))
    }

    @Test
    fun testHsmPinAuthentication() {
        val hsm = HsmCryptoTokenProtocolEngine()
        assertFalse(hsm.authenticatePin("123")) // Too short
        assertFalse(hsm.authenticatePin("abcdef")) // Non-digits

        assertTrue(hsm.authenticatePin("123456"))
        assertEquals(HsmSessionState.PIN_AUTHENTICATED, hsm.getSessionState())
    }

    @Test
    fun testHsmKeyPairGenerationAndFingerprint() {
        val hsm = HsmCryptoTokenProtocolEngine()
        hsm.authenticatePin("123456")
        val fingerprint = hsm.loadOrGenerateKey("LEGAL-ROOT-KEY")

        assertNotNull(fingerprint)
        assertEquals(16, fingerprint.length) // 8 bytes in hex = 16 characters
        assertEquals(HsmSessionState.DIGITAL_SIGNING_READY, hsm.getSessionState())
    }

    @Test
    fun testDigitalSignatureGenerationAndVerification() {
        val hsm = HsmCryptoTokenProtocolEngine()
        hsm.authenticatePin("987654")
        hsm.loadOrGenerateKey("ATTORNEY-SIGNING-KEY")

        val docHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val sigResult = hsm.signDocumentHash(docHash, "ATTORNEY-SIGNING-KEY")

        assertNotNull(sigResult)
        assertEquals(64, sigResult!!.signatureHex.length)
        assertTrue(sigResult.najizAuditReference.startsWith("NJZ-"))
        assertTrue(hsm.verifySignature(docHash, sigResult.signatureHex))
    }

    @Test
    fun testStandardLegalClausesRoster() {
        val engine = CertifiedLegalContractEngine()
        val clauses = engine.getStandardClauses()

        assertEquals(3, clauses.size)
        val jurisdiction = clauses.find { it.clauseCode == "CLS-03" }
        assertNotNull(jurisdiction)
        assertTrue(jurisdiction!!.bodyArabic.contains("المحاكم التجارية"))
    }

    @Test
    fun testDraftContractCreationAndHashing() {
        val engine = CertifiedLegalContractEngine()
        val contract = engine.createDraftContract(
            title = "عقد استشارات نظامية",
            classification = ContractClassification.LEGAL_RETAINER,
            client = "شركة الاتصالات المتقدمة",
            attorneyCr = "CR-1010-998877",
            valueSar = 120000.0
        )

        assertNotNull(contract)
        assertTrue(contract.contractId.startsWith("LGL-"))
        assertEquals(64, contract.sha256Fingerprint.length)
        assertEquals(3, contract.clauses.size)
        assertFalse(contract.isNajizCertified)
    }

    @Test
    fun testAddClauseToContractAndHashUpdate() {
        val engine = CertifiedLegalContractEngine()
        val contract = engine.createDraftContract(
            title = "اتفاقية عدم إفصاح",
            classification = ContractClassification.NON_DISCLOSURE_AGREEMENT,
            client = "مؤسسة التقنية الذكية",
            attorneyCr = "CR-1010-554433",
            valueSar = 0.0
        )
        val initialHash = contract.sha256Fingerprint

        val customClause = LegalClause("CLS-CUSTOM", "التعويض الاتفاقي", "يلتزم الطرف المخل بدفع تعويض اتفاقي قدره خمسون ألف ريال.")
        val added = engine.addClauseToContract(contract.contractId, customClause)
        assertTrue(added)

        val updated = engine.getContracts().find { it.contractId == contract.contractId }
        assertNotNull(updated)
        assertEquals(4, updated!!.clauses.size)
        assertNotEquals(initialHash, updated.sha256Fingerprint)
    }

    @Test
    fun testCertifyContractWithHsmAndNajizLegitimacy() {
        val hsm = HsmCryptoTokenProtocolEngine()
        hsm.authenticatePin("123456")
        hsm.loadOrGenerateKey("CHAMBERS-ROOT-KEY")

        val contractEngine = CertifiedLegalContractEngine()
        val contract = contractEngine.createDraftContract(
            title = "عقد وكالة شرعية",
            classification = ContractClassification.POWER_OF_ATTORNEY_WAKALAH,
            client = "مجموعة الراجحي للمقاولات",
            attorneyCr = "CR-1010-112233",
            valueSar = 50000.0
        )

        val certified = contractEngine.certifyContractWithHsm(contract.contractId, hsm, "CHAMBERS-ROOT-KEY")
        assertNotNull(certified)
        assertTrue(certified!!.isNajizCertified)
        assertNotNull(certified.signatureResult)
        assertTrue(contractEngine.verifyNajizLegitimacy(contract.contractId))
    }

    @Test
    fun testHardwareSensorDriverLiveStreamFrames() = runBlocking {
        val driver = HardwareSensorDriver()
        assertNotNull(driver.getHsmEngine())
        assertNotNull(driver.getContractEngine())

        val firstFrame = driver.observePeripheralStream().first()
        assertEquals("FIPS 140-2 Cryptographic HSM Security Token", firstFrame.peripheralName)
        assertEquals("CONNECTING", firstFrame.connectionState)
    }
}
