package com.example.businesscardscanner.ocr

data class ParsedBusinessCard(
    val name: OcrField = OcrField(),
    val company: OcrField = OcrField(),
    val jobTitle: OcrField = OcrField(),
    val phone: OcrField = OcrField(),
    val email: OcrField = OcrField(),
    val website: OcrField = OcrField(),
    val address: OcrField = OcrField(),
    val social: OcrField = OcrField(),
    val category: OcrField = OcrField(),
    val values: Map<String, String> = emptyMap()
)

object BusinessCardOcrParser {
    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val websiteRegex = Regex("(https?://\\S+|www\\.\\S+|[A-Za-z0-9.-]+\\.[A-Za-z]{2,})", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(\\+?\\d[\\d\\s().-]{7,}\\d)")
    private val socialRegex = Regex("(@[A-Za-z0-9_.-]+|linkedin\\.com/\\S+|instagram\\.com/\\S+|facebook\\.com/\\S+|x\\.com/\\S+|twitter\\.com/\\S+)", RegexOption.IGNORE_CASE)

    private val jobTitleKeywords = listOf(
        "engineer", "developer", "manager", "director", "ceo", "cto", "founder",
        "designer", "consultant", "architect", "officer", "lead", "marketing", "sales", "product"
    )

    private val companyKeywords = listOf(
        "inc", "ltd", "llc", "corp", "company", "co.", "solutions", "studio", "labs", "technologies", "systems", "group", "agency"
    )

    private val addressKeywords = listOf(
        "street", "st.", "road", "rd.", "avenue", "ave.", "lane", "ln.", "suite", "floor",
        "building", "block", "sector", "phase", "blue area", "pakistan", "islamabad",
        "karachi", "lahore", "rawalpindi", "faisalabad", "peshawar", "multan"
    )

    fun parse(rawText: String, blocks: List<RecognizedTextBlock> = emptyList()): ParsedBusinessCard {
        val normalizedBlocks = if (blocks.isNotEmpty()) {
            blocks.sortedWith(compareBy<RecognizedTextBlock> { it.y }.thenBy { it.x })
        } else {
            rawText.lines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) null else RecognizedTextBlock(trimmed, 0, index * 10, trimmed.length * 10, 20, index)
            }
        }

        val emailMatches = emailRegex.findAll(rawText).map { it.value }.distinct().toList()
        val websiteMatches = websiteRegex.findAll(rawText).map { normalizeWebsite(it.value) }.distinct().toList()
        val phoneMatches = phoneRegex.findAll(rawText).map { normalizePhone(it.value) }.distinct().toList()
        val socialMatches = socialRegex.findAll(rawText).map { it.value }.distinct().toList()

        val email = emailMatches.firstOrNull().orEmpty()
        val website = websiteMatches.firstOrNull().orEmpty()
        val phone = phoneMatches.distinct().joinToString(", ")
        val social = socialMatches.firstOrNull().orEmpty()

        val textPool = normalizedBlocks.map { it.text }.filter { text ->
            emailMatches.none { emailCandidate -> text.contains(emailCandidate, ignoreCase = true) } &&
                websiteMatches.none { websiteCandidate -> text.contains(websiteCandidate, ignoreCase = true) } &&
                phoneMatches.none { phoneCandidate -> text.contains(phoneCandidate, ignoreCase = true) } &&
                socialMatches.none { socialCandidate -> text.contains(socialCandidate, ignoreCase = true) }
        }

        val name = detectName(normalizedBlocks, textPool, rawText)
        val jobTitle = detectJobTitle(normalizedBlocks, textPool, name.value)
        val company = detectCompany(normalizedBlocks, textPool, name.value, jobTitle.value)
        val address = detectAddress(normalizedBlocks, textPool, name.value, company.value, jobTitle.value, email, phone, website)
        val category = if (rawText.contains("vip", true)) "VIP" else if (rawText.contains("colleague", true)) "Colleague" else ""

        val values = linkedMapOf<String, String>()
        if (name.value.isNotBlank()) values["name"] = name.value
        if (company.value.isNotBlank()) values["company"] = company.value
        if (jobTitle.value.isNotBlank()) values["jobTitle"] = jobTitle.value
        if (phone.isNotBlank()) values["phone"] = phone
        if (email.isNotBlank()) values["email"] = email
        if (website.isNotBlank()) values["website"] = website
        if (address.value.isNotBlank()) values["address"] = address.value
        if (social.isNotBlank()) values["social"] = social
        if (category.isNotBlank()) values["group"] = category

        return ParsedBusinessCard(
            name = name,
            company = company,
            jobTitle = jobTitle,
            phone = score(phone),
            email = score(email),
            website = score(website),
            address = address,
            social = score(social),
            category = score(category),
            values = values
        )
    }

    fun parseQrPayload(rawPayload: String): OcrResult {
        val normalized = rawPayload.trim()
        val fields = parseVCard(normalized)
        return OcrResult(
            rawText = normalized,
            name = score(fields["name"].orEmpty(), 90),
            company = score(fields["company"].orEmpty(), 90),
            jobTitle = score(fields["jobTitle"].orEmpty(), 90),
            phone = score(fields["phone"].orEmpty(), 90),
            email = score(fields["email"].orEmpty(), 90),
            website = score(fields["website"].orEmpty(), 90),
            address = score(fields["address"].orEmpty(), 90),
            category = score(fields["category"].orEmpty(), 60),
            qrText = normalized,
            parsedValues = fields
        )
    }

    private fun detectName(blocks: List<RecognizedTextBlock>, lines: List<String>, rawText: String): OcrField {
        val candidate = blocks
            .map { block -> block to scoreNameCandidate(block, blocks) }
            .sortedByDescending { it.second }
            .firstOrNull { it.second > 0 }
            ?.first
            ?.text
            .orEmpty()
        return score(candidate, when {
            candidate.isBlank() -> 0
            scoreNameCandidate(RecognizedTextBlock(candidate, 0, 0, candidate.length * 10, 20, 0), blocks) >= 90 -> 92
            rawText.contains(candidate) -> 82
            else -> 68
        })
    }

    private fun detectCompany(blocks: List<RecognizedTextBlock>, lines: List<String>, name: String, jobTitle: String): OcrField {
        val candidate = blocks
            .map { block -> block to scoreCompanyCandidate(block, blocks, name, jobTitle) }
            .sortedByDescending { it.second }
            .firstOrNull { it.second > 0 }
            ?.first
            ?.text
            .orEmpty()
        return score(candidate, when {
            candidate.isBlank() -> 0
            scoreCompanyCandidate(RecognizedTextBlock(candidate, 0, 0, candidate.length * 10, 20, 0), blocks, name, jobTitle) >= 90 -> 90
            containsAny(candidate.lowercase(), companyKeywords) -> 86
            else -> 64
        })
    }

    private fun detectJobTitle(blocks: List<RecognizedTextBlock>, lines: List<String>, name: String): OcrField {
        val candidate = blocks.firstOrNull { block ->
            val lower = block.text.lowercase()
            containsAny(lower, jobTitleKeywords) && lower.length in 5..45
        }?.text.orEmpty().ifBlank {
            lines.firstOrNull { line ->
                val lower = line.lowercase()
                lower != name.lowercase() && containsAny(lower, jobTitleKeywords) && lower.length in 5..45
            }.orEmpty()
        }
        return score(candidate, if (candidate.isBlank()) 0 else 82)
    }

    private fun detectAddress(
        blocks: List<RecognizedTextBlock>,
        lines: List<String>,
        name: String,
        company: String,
        jobTitle: String,
        email: String,
        phone: String,
        website: String
    ): OcrField {
        val ignored = setOf(name.lowercase(), company.lowercase(), jobTitle.lowercase())
        val addressLines = lines.filter { line ->
            val lower = line.lowercase()
            lower !in ignored &&
                email.isBlank().or { !lower.contains(email.lowercase()) } &&
                phone.isBlank().or { !lower.contains(phone.lowercase()) } &&
                website.isBlank().or { !lower.contains(website.lowercase()) } &&
                (lower.any { it.isDigit() } || containsAny(lower, addressKeywords))
        }
        val candidate = if (addressLines.isNotEmpty()) {
            addressLines.takeLast(4).joinToString(", ").trim()
        } else {
            blocks.lastOrNull { block ->
                val lower = block.text.lowercase()
                lower !in ignored &&
                    email.isBlank().or { !lower.contains(email.lowercase()) } &&
                    phone.isBlank().or { !lower.contains(phone.lowercase()) } &&
                    website.isBlank().or { !lower.contains(website.lowercase()) }
            }?.text.orEmpty()
        }
        return score(candidate, if (candidate.isBlank()) 0 else 76)
    }

    private fun normalizePhone(value: String): String {
        val cleaned = value.replace(Regex("[^+\\d]"), "")
        return cleaned
    }

    private fun normalizeWebsite(value: String): String {
        val trimmed = value.trim().removeSuffix(".")
        return when {
            trimmed.startsWith("http", true) -> trimmed
            trimmed.startsWith("www.", true) -> "https://$trimmed"
            else -> "https://$trimmed"
        }
    }

    private fun containsAny(text: String, keywords: List<String>) = keywords.any { text.contains(it, ignoreCase = true) }

    private fun score(value: String, confidence: Int = if (value.isBlank()) 0 else 80) = OcrField(value, confidence)

    private fun Boolean.or(block: () -> Boolean): Boolean = this || block()

    private fun scoreNameCandidate(block: RecognizedTextBlock, blocks: List<RecognizedTextBlock>): Int {
        val trimmed = block.text.trim()
        val lower = trimmed.lowercase()
        if (trimmed.length !in 4..40) return 0
        if (trimmed.any { it.isDigit() }) return 0
        if (containsAny(lower, jobTitleKeywords) || containsAny(lower, companyKeywords) || containsAny(lower, addressKeywords)) return 0
        val letters = trimmed.count { it.isLetter() }
        if (letters < 4) return 0
        var score = 40 + letters.coerceAtMost(12)
        if (Regex("^[A-Z][a-z]+(\\s+[A-Z][a-z]+){1,2}$").matches(trimmed)) score += 35
        if (Regex("^[A-Z][A-Za-z'.-]+(\\s+[A-Z][A-Za-z'.-]+){1,3}$").matches(trimmed)) score += 25
        if (trimmed == trimmed.uppercase()) score -= 10
        if (block.y <= blocks.minOfOrNull { it.y } ?: 0) score += 12
        if (block.y <= ((blocks.minByOrNull { it.y }?.y ?: 0) + 80)) score += 5
        if (block.height >= blocks.maxOfOrNull { it.height } ?: block.height) score += 15
        if (block.x <= ((blocks.maxOfOrNull { it.x }?.div(2)) ?: 0)) score += 8
        return score.coerceIn(0, 100)
    }

    private fun scoreCompanyCandidate(block: RecognizedTextBlock, blocks: List<RecognizedTextBlock>, name: String, jobTitle: String): Int {
        val trimmed = block.text.trim()
        val lower = trimmed.lowercase()
        if (trimmed.isBlank()) return 0
        if (trimmed.equals(name, true) || trimmed.equals(jobTitle, true)) return 0
        if (trimmed.length !in 2..60) return 0
        var score = 30
        if (containsAny(lower, companyKeywords)) score += 45
        if (trimmed == trimmed.uppercase() && trimmed.any { it.isLetter() }) score += 10
        if (trimmed.any { it.isDigit() }) score += 5
        if (trimmed.split(Regex("\\s+")).size <= 4) score += 5
        if (block.y <= ((blocks.minOfOrNull { it.y } ?: 0) + 120)) score += 10
        if (block.x <= ((blocks.maxOfOrNull { it.x }?.div(2)) ?: 0)) score += 8
        if (block.height >= ((blocks.maxOfOrNull { it.height } ?: block.height) / 2)) score += 6
        if (Regex(".*(Solutions|Technologies|Systems|Group|Studio|Labs|Agency|Company|Corp|Inc|Ltd|LLC).*", RegexOption.IGNORE_CASE).matches(trimmed)) {
            score += 15
        }
        if (Regex("^[A-Z][a-z]+\\s+[A-Z][a-z]+$").matches(trimmed)) score -= 20
        if (Regex("^[A-Z][a-z]+\\s+[A-Z][a-z]+\\s+[A-Z][a-z]+$").matches(trimmed)) score -= 10
        return score.coerceIn(0, 100)
    }

    private fun parseVCard(raw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        if (!raw.contains("BEGIN:VCARD", ignoreCase = true) && !raw.contains("MECARD:", ignoreCase = true)) {
            return result
        }
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("FN:", true) -> result["name"] = trimmed.substringAfter("FN:", "").trim()
                trimmed.startsWith("N:", true) && result["name"].isNullOrBlank() -> result["name"] = trimmed.substringAfter("N:", "").trim().replace(';', ' ')
                trimmed.startsWith("ORG:", true) -> result["company"] = trimmed.substringAfter("ORG:", "").trim()
                trimmed.startsWith("TITLE:", true) -> result["jobTitle"] = trimmed.substringAfter("TITLE:", "").trim()
                trimmed.startsWith("TEL", true) -> result.putIfAbsent("phone", trimmed.substringAfter(':', "").trim())
                trimmed.startsWith("EMAIL", true) -> result["email"] = trimmed.substringAfter(':', "").trim()
                trimmed.startsWith("URL", true) -> result["website"] = trimmed.substringAfter(':', "").trim()
                trimmed.startsWith("ADR", true) -> result["address"] = trimmed.substringAfter(':', "").trim().replace(';', ',').trim(',',' ')
            }
        }
        if (raw.startsWith("MECARD:", true)) {
            raw.removePrefix("MECARD:").split(';').forEach { chunk ->
                val key = chunk.substringBefore(':', "").trim().uppercase()
                val value = chunk.substringAfter(':', "").trim()
                when (key) {
                    "N" -> result["name"] = value
                    "ORG" -> result["company"] = value
                    "TITLE" -> result["jobTitle"] = value
                    "TEL" -> result.putIfAbsent("phone", value)
                    "EMAIL" -> result["email"] = value
                    "URL" -> result["website"] = value
                    "ADR" -> result["address"] = value
                }
            }
        }
        return result
    }
}
