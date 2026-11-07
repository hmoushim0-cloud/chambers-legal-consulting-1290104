package sa.factory.chamberslegalconsu.engine

import java.security.MessageDigest

enum class ContractClassification(val arabicName: String) {
    COMMERCIAL_PARTNERSHIP("عقد شراكة تجارية واستثمارية"),
    NON_DISCLOSURE_AGREEMENT("اتفاقية سرية المعلومات وعدم الإفصاح"),
    POWER_OF_ATTORNEY_WAKALAH("سند وكالة شرعية ونظامية"),
    LEGAL_RETAINER("عقد تقديم استشارات قانونية سنوية")
}

data class LegalClause(
    val clauseCode: String,
    val titleArabic: String,
    val bodyArabic: String,
    val isMandatory: Boolean = true
)

data class CertifiedLegalContract(
    val contractId: String,
    val titleArabic: String,
    val classification: ContractClassification,
    val clientName: String,
    val attorneyCrNumber: String,
    val clauses: List<LegalClause>,
    val totalValueSar: Double,
    val sha256Fingerprint: String,
    val isNajizCertified: Boolean = false,
    val signatureResult: CryptoSignatureResult? = null
)

class CertifiedLegalContractEngine {
    private val standardClauses = listOf(
        LegalClause("CLS-01", "أهلية التعاقد", "أقر الطرفان بأهليتهما الشرعية والنظامية المعتبرة شرعًا ونظامًا لإبرام هذا العقد."),
        LegalClause("CLS-02", "سرية البيانات والمستندات", "يلتزم الطرفان بالحفاظ على سرية المستندات والمعلومات المتبادلة وفق الأنظمة المرعية بالمملكة."),
        LegalClause("CLS-03", "الاختصاص القضائي", "تختص المحاكم التجارية بمدينة الرياض بالنظر في أي نزاع ينشأ عن تفسير أو تنفيذ هذا العقد.")
    )

    private val contracts = mutableListOf<CertifiedLegalContract>()

    fun getStandardClauses(): List<LegalClause> = standardClauses
    fun getContracts(): List<CertifiedLegalContract> = contracts.toList()

    fun createDraftContract(
        title: String,
        classification: ContractClassification,
        client: String,
        attorneyCr: String,
        valueSar: Double
    ): CertifiedLegalContract {
        val cid = "LGL-${System.currentTimeMillis() % 100000}"
        val initialClauses = standardClauses.toMutableList()

        val rawContent = "$cid-$title-$client-$attorneyCr-$valueSar"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(rawContent.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        val draft = CertifiedLegalContract(
            contractId = cid,
            titleArabic = title,
            classification = classification,
            clientName = client,
            attorneyCrNumber = attorneyCr,
            clauses = initialClauses,
            totalValueSar = valueSar,
            sha256Fingerprint = hash,
            isNajizCertified = false
        )
        contracts.add(draft)
        return draft
    }

    fun addClauseToContract(contractId: String, clause: LegalClause): Boolean {
        val index = contracts.indexOfFirst { it.contractId == contractId }
        if (index == -1) return false

        val existing = contracts[index]
        val updatedClauses = existing.clauses.toMutableList()
        updatedClauses.add(clause)

        // Recompute hash
        val rawContent = "${existing.contractId}-${existing.titleArabic}-${updatedClauses.size}"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(rawContent.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        contracts[index] = existing.copy(clauses = updatedClauses, sha256Fingerprint = hash)
        return true
    }

    fun certifyContractWithHsm(
        contractId: String,
        hsmEngine: HsmCryptoTokenProtocolEngine,
        keyAlias: String = "CHAMBERS-ROOT-KEY"
    ): CertifiedLegalContract? {
        val index = contracts.indexOfFirst { it.contractId == contractId }
        if (index == -1) return null

        val contract = contracts[index]
        val sig = hsmEngine.signDocumentHash(contract.sha256Fingerprint, keyAlias) ?: return null

        val certified = contract.copy(
            isNajizCertified = true,
            signatureResult = sig
        )
        contracts[index] = certified
        return certified
    }

    fun verifyNajizLegitimacy(contractId: String): Boolean {
        val contract = contracts.find { it.contractId == contractId } ?: return false
        return contract.isNajizCertified && contract.signatureResult != null
    }
}
