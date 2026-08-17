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
    val phoneSecondary: OcrField = OcrField(),
    val category: OcrField = OcrField(),
    val description: OcrField = OcrField(),
    
    // New detailed fields
    val department: OcrField = OcrField(),
    val mobile: OcrField = OcrField(),
    val officeNumber: OcrField = OcrField(),
    val fax: OcrField = OcrField(),
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val city: OcrField = OcrField(),
    val country: OcrField = OcrField(),
    val postalCode: OcrField = OcrField(),
    val linkedin: OcrField = OcrField(),
    val facebook: OcrField = OcrField(),
    val instagram: OcrField = OcrField(),
    val twitter: OcrField = OcrField(),
    val notes: OcrField = OcrField(),

    val values: Map<String, String> = emptyMap(),
    val overallConfidence: Int = 0
)

object BusinessCardOcrParser {
    private val emailRegex = Regex("[a-zA-Z0-9._%+-]+(?:@|\\(a\\)|\\(A\\))[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val websiteRegex = Regex("(https?://\\S+|www\\.\\S+|[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,63}(?:/\\S*)?)", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?:\\+?\\d[\\d\\s().-]{7,}\\d)")
    private val socialRegex = Regex("(@[a-zA-Z0-9_.-]+|linkedin\\.com/\\S+|instagram\\.com/\\S+|facebook\\.com/\\S+|x\\.com/\\S+|twitter\\.com/\\S+)", RegexOption.IGNORE_CASE)

    // Expanded job title keywords to include international titles
    private val jobTitleKeywords = listOf(
        "engineer", "developer", "manager", "director", "ceo", "cto", "founder",
        "designer", "consultant", "architect", "officer", "lead", "marketing", "sales", "product",
        "owner", "proprietor", "managing director", "partner", "executive", "sales representative", "general manager", "chairman"
    )

    // Expanded company suffix keywords to include international suffixes
    private val companyKeywords = listOf(
        "inc", "ltd", "llc", "corp", "company", "co.", "solutions", "studio", "labs", "technologies", "systems", "group", "agency",
        "pvt ltd", "(private) limited", "gmbh", "s.a.", "s.p.a.", "s.a.r.l.", "trading", "enterprises", "& sons", "& co.", "traders", "industries", "corporation", "plc", "bv", "ag"
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

        // Step A: Regex extraction
        // Use raw matches for filtering, normalized for values
        val emailRaw = emailRegex.findAll(rawText).map { it.value }.distinct().toList()
        val websiteRaw = websiteRegex.findAll(rawText).map { it.value }.distinct().toList()
        
        val phoneUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance()
        var defaultRegion = java.util.Locale.getDefault().country.ifBlank { "US" }
        
        // Infer default region from text to handle local numbers without + code
        val rawLower = rawText.lowercase()
        if (rawLower.contains("pakistan") || rawLower.contains("pk") || rawLower.contains("lahore") || rawLower.contains("karachi") || rawLower.contains("islamabad")) {
            defaultRegion = "PK"
        } else if (rawLower.contains("uk") || rawLower.contains("united kingdom") || rawLower.contains("london")) {
            defaultRegion = "GB"
        } else if (rawLower.contains("uae") || rawLower.contains("dubai") || rawLower.contains("united arab emirates")) {
            defaultRegion = "AE"
        }
        
        val validPhoneNumbers = mutableListOf<String>()
        val phoneRawMatchesForExclusion = mutableListOf<String>()
        
        // Use libphonenumber's highly accurate findNumbers iterator instead of raw regex
        val phoneMatches = phoneUtil.findNumbers(rawText, defaultRegion)
        for (match in phoneMatches) {
            val rawStr = match.rawString()
            val formatted = if (rawStr.trim().startsWith("+") || rawStr.trim().startsWith("00")) {
                phoneUtil.format(match.number(), com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            } else {
                rawStr.replace(Regex("[^0-9+\\s().-]"), "").trim()
            }
            validPhoneNumbers.add(formatted)
            phoneRawMatchesForExclusion.add(rawStr)
        }
        
        // Also use our regex fallback for numbers libphonenumber might miss (e.g. local numbers without country code on incorrect locale)
        val phoneRegexMatches = phoneRegex.findAll(rawText).map { it.value }.distinct().toList()
        for (match in phoneRegexMatches) {
            // Check if this match is already in our exclusion list to avoid duplicates
            if (phoneRawMatchesForExclusion.none { it.contains(match) || match.contains(it) }) {
                val formatted = match.replace(Regex("[^0-9+\\s().-]"), "").trim()
                if (formatted.count { it.isDigit() } >= 7) {
                    validPhoneNumbers.add(formatted)
                    phoneRawMatchesForExclusion.add(match)
                }
            }
        }
        
        val validPhonesDistinct = validPhoneNumbers.distinct()

        val socialRaw = socialRegex.findAll(rawText).map { it.value }.distinct().toList()

        val emails = emailRaw.map { it.replace("(a)", "@", true) }
        val email = emails.firstOrNull().orEmpty()
        val website = websiteRaw.map { normalizeWebsite(it) }.firstOrNull().orEmpty()
        
        var mobile = ""
        var office = ""
        var fax = ""
        val phones = validPhonesDistinct
        val phone1 = validPhonesDistinct.getOrNull(0).orEmpty()
        val phone2 = validPhonesDistinct.getOrNull(1).orEmpty()
        
        // Classify phones
        for (i in validPhonesDistinct.indices) {
            val phoneNum = validPhonesDistinct[i]
            val rawStr = phoneRawMatchesForExclusion.getOrNull(i) ?: ""
            // Look at surrounding context in raw text for classification
            val index = rawText.indexOf(rawStr)
            if (index != -1) {
                val context = rawText.substring(maxOf(0, index - 20), index).lowercase()
                if (context.contains("m") || context.contains("mob") || context.contains("cell")) {
                    mobile = phoneNum
                } else if (context.contains("f") || context.contains("fax")) {
                    fax = phoneNum
                } else if (context.contains("o") || context.contains("off") || context.contains("tel") || context.contains("work")) {
                    office = phoneNum
                }
            }
        }
        
        if (mobile.isBlank() && phones.isNotEmpty()) mobile = phones[0]
        if (office.isBlank() && phones.size > 1) office = phones[1]

        val social = socialRaw.firstOrNull().orEmpty()
        var linkedin = ""
        var facebook = ""
        var instagram = ""
        var twitter = ""
        for (s in socialRaw) {
            val lower = s.lowercase()
            if (lower.contains("linkedin")) linkedin = s
            else if (lower.contains("facebook")) facebook = s
            else if (lower.contains("instagram")) instagram = s
            else if (lower.contains("twitter") || lower.contains("x.com")) twitter = s
        }

        // Remove explicitly matched items from the pool using RAW matches
        val validBlocks = normalizedBlocks.filter { block ->
            val text = block.text
            emailRaw.none { text.contains(it, ignoreCase = true) } &&
            websiteRaw.none { text.contains(it, ignoreCase = true) } &&
            phoneRawMatchesForExclusion.none { text.contains(it, ignoreCase = true) } &&
            socialRaw.none { text.contains(it, ignoreCase = true) }
        }

        // Find Y position of top-most phone block
        val phoneBlocks = normalizedBlocks.filter { block ->
            phoneRawMatchesForExclusion.any { block.text.contains(it, ignoreCase = true) }
        }
        val phoneMinY = phoneBlocks.minOfOrNull { it.y } ?: Int.MAX_VALUE

        // Step B: Typographic hierarchy & keyword scoring
        val blockScores = validBlocks.map { block ->
            val nScore = scoreNameCandidate(block, validBlocks, phoneMinY)
            val cScore = scoreCompanyCandidate(block, validBlocks, "", "")
            Triple(block, nScore, cScore)
        }
        
        var nameBlock: RecognizedTextBlock? = null
        var companyBlock: RecognizedTextBlock? = null
        var jobTitleBlock: RecognizedTextBlock? = null
        
        val sortedForName = blockScores.sortedByDescending { it.second }.filter { it.second >= 30 }
        val sortedForCompany = blockScores.sortedByDescending { it.third }.filter { it.third >= 30 }
        
        val bestName = sortedForName.firstOrNull()
        val bestCompany = sortedForCompany.firstOrNull()
        
        if (bestName != null && bestCompany != null && bestName.first == bestCompany.first) {
            // Disambiguate if the highest scoring block is the same for both
            if (bestName.second > bestCompany.third) {
                nameBlock = bestName.first
                companyBlock = sortedForCompany.getOrNull(1)?.first
            } else if (bestCompany.third > bestName.second) {
                companyBlock = bestCompany.first
                nameBlock = sortedForName.getOrNull(1)?.first
            } else {
                // If equal, fallback to Name
                nameBlock = bestName.first
                companyBlock = sortedForCompany.getOrNull(1)?.first
            }
        } else {
            nameBlock = bestName?.first
            companyBlock = bestCompany?.first
        }
        
        // Job Title: Look for title keywords in the remaining blocks, prioritizing larger height
        val sortedByHeight = validBlocks.sortedByDescending { it.height }
        val remainingAfterNameCompany = sortedByHeight.filter { it != nameBlock && it != companyBlock }
        jobTitleBlock = remainingAfterNameCompany.firstOrNull { block ->
            val isJobKeyword = containsAny(block.text.lowercase(), jobTitleKeywords)
            val isServiceDescription = nameBlock != null && block.y > nameBlock.y && block.y < nameBlock.y + nameBlock.height * 3 && block.height < nameBlock.height && !containsAny(block.text.lowercase(), companyKeywords)
            (isJobKeyword || isServiceDescription) && 
            block.text.length in 2..50 && 
            block.text.count { it.isDigit() } <= 2
        }

        // Step C: Address fallback
        // Combine remaining blocks that are spatially close (same blockIndex or close Y)
        val remainingForAddress = remainingAfterNameCompany.filter { it != jobTitleBlock }.sortedBy { it.y }
        var addressText = ""
        var addressParagraphBlocks = emptyList<RecognizedTextBlock>()
        
        if (remainingForAddress.isNotEmpty()) {
            // Group blocks into paragraphs based on vertical proximity
            val paragraphs = mutableListOf<MutableList<RecognizedTextBlock>>()
            var currentParagraph = mutableListOf(remainingForAddress.first())
            
            for (i in 1 until remainingForAddress.size) {
                val current = remainingForAddress[i]
                val prev = currentParagraph.last()
                
                // If the next line is within 1.5x the height of the previous line, consider it part of the same paragraph
                if (current.y - (prev.y + prev.height) < prev.height * 1.5) {
                    currentParagraph.add(current)
                } else {
                    paragraphs.add(currentParagraph)
                    currentParagraph = mutableListOf(current)
                }
            }
            paragraphs.add(currentParagraph)
            
            // Find the best address paragraph
            val addressParagraph = paragraphs.maxByOrNull { para ->
                val text = para.joinToString(" ") { it.text.lowercase() }
                var score = para.size * 10 // prefer multi-line
                if (containsAny(text, addressKeywords)) score += 50
                if (text.any { it.isDigit() }) score += 20
                // Light regex for postal codes/zip codes (5 digits or alphanumeric patterns like UK/Canada)
                if (Regex("\\b\\d{5}(?:-\\d{4})?\\b|\\b[A-Z]{1,2}\\d[A-Z\\d]? \\d[A-Z]{2}\\b|\\b[A-Z]\\d[A-Z] \\d[A-Z]\\d\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                    score += 40
                }
                score
            }
            
            if (addressParagraph != null) {
                val fullText = addressParagraph.joinToString(", ") { it.text }
                // Require some alphanumeric content and a minimum score to avoid random single lines
                val score = addressParagraph.size * 10 + (if (containsAny(fullText.lowercase(), addressKeywords)) 50 else 0) + (if (fullText.any { it.isDigit() }) 20 else 0)
                if (fullText.any { it.isLetter() } && score >= 20) {
                    addressText = fullText
                    addressParagraphBlocks = addressParagraph
                }
            }
        }
        
        // Collect unused information into description
        val usedBlocks = listOfNotNull(nameBlock, companyBlock, jobTitleBlock).toSet() + addressParagraphBlocks.toSet()
        val unusedBlocks = validBlocks.filter { it !in usedBlocks }
        val descriptionText = unusedBlocks.joinToString("\n") { it.text.trim() }.trim()
        
        val category = if (rawText.contains("vip", true)) "VIP" else if (rawText.contains("colleague", true)) "Colleague" else ""

        val name = score(nameBlock?.text.orEmpty(), if (nameBlock != null) 90 else 0)
        val company = score(companyBlock?.text.orEmpty(), if (companyBlock != null) 90 else 0)
        val jobTitle = score(jobTitleBlock?.text.orEmpty(), if (jobTitleBlock != null) 85 else 0)
        
        // Simple heuristic for department: a line near company/job title that has typical department words
        val deptKeywords = listOf("department", "dept", "division", "team", "group", "operations", "engineering", "sales", "marketing", "hr")
        var department = ""
        val remainingForDept = unusedBlocks.filter { containsAny(it.text, deptKeywords) }
        if (remainingForDept.isNotEmpty()) {
            department = remainingForDept.first().text
        }
        
        // Simple heuristic for city/country/postal code from address
        var city = ""
        var country = ""
        var postalCode = ""
        if (addressText.isNotBlank()) {
            val addressParts = addressText.split(",").map { it.trim() }
            if (addressParts.size >= 3) {
                country = addressParts.last()
                val cityZip = addressParts[addressParts.size - 2]
                val zipRegex = Regex("\\b\\d{4,6}\\b|\\b[A-Z\\d\\s-]{5,10}\\b")
                val zipMatch = zipRegex.find(cityZip)
                if (zipMatch != null) {
                    postalCode = zipMatch.value
                    city = cityZip.replace(postalCode, "").trim()
                } else {
                    city = cityZip
                }
            }
        }

        val address = score(addressText, if (addressText.isNotBlank()) 75 else 0)
        val description = score(descriptionText, if (descriptionText.isNotBlank()) 50 else 0)
        
        val values = linkedMapOf<String, String>()
        if (name.value.isNotBlank()) values["name"] = name.value
        if (company.value.isNotBlank()) values["company"] = company.value
        if (jobTitle.value.isNotBlank()) values["jobTitle"] = jobTitle.value
        if (phone1.isNotBlank()) values["phone"] = phone1
        if (phone2.isNotBlank()) values["phoneSecondary"] = phone2
        if (email.isNotBlank()) values["email"] = email
        if (website.isNotBlank()) values["website"] = website
        if (address.value.isNotBlank()) values["address"] = address.value
        if (social.isNotBlank()) values["social"] = social
        if (category.isNotBlank()) values["group"] = category
        if (description.value.isNotBlank()) values["description"] = description.value
        // Generate internal confidence score based on the ratio of valid extracted entities to total words
        val words = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val totalWords = words.size
        var confidenceScore = 0
        if (totalWords > 0) {
            val matchedWords = (
                name.value.split(Regex("\\s+")).size +
                company.value.split(Regex("\\s+")).size +
                jobTitle.value.split(Regex("\\s+")).size +
                (if (phone1.isNotBlank()) 1 else 0) +
                (if (email.isNotBlank()) 1 else 0) +
                (if (website.isNotBlank()) 1 else 0)
            )
            // Weight standard business card fields
            confidenceScore = ((matchedWords.toFloat() / totalWords.coerceAtLeast(10)) * 100).toInt().coerceIn(0, 100)
            // Add a bonus if core regex patterns (phone/email) were found
            if (phone1.isNotBlank()) confidenceScore += 15
            if (email.isNotBlank()) confidenceScore += 15
            if (name.value.isNotBlank()) confidenceScore += 10
            confidenceScore = confidenceScore.coerceIn(0, 100)
        }

        return ParsedBusinessCard(
            name = name,
            company = company,
            jobTitle = jobTitle,
            phone = score(phone1),
            phoneSecondary = score(phone2),
            email = score(email),
            website = score(website),
            address = address,
            social = score(social),
            category = score(category),
            description = description,
            department = score(department),
            mobile = score(mobile),
            officeNumber = score(office),
            fax = score(fax),
            phoneNumbers = phones,
            emails = emails,
            city = score(city),
            country = score(country),
            postalCode = score(postalCode),
            linkedin = score(linkedin),
            facebook = score(facebook),
            instagram = score(instagram),
            twitter = score(twitter),
            notes = description,
            values = values,
            overallConfidence = confidenceScore
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

    private fun normalizePhone(value: String): String {
        // Obsolete, using PhoneNumberUtil now
        return value.replace(Regex("[^+\\d]"), "")
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

    private fun scoreNameCandidate(block: RecognizedTextBlock, blocks: List<RecognizedTextBlock>, phoneMinY: Int = Int.MAX_VALUE): Int {
        val trimmed = block.text.trim()
        val lower = trimmed.lowercase()
        if (trimmed.length !in 4..40) return 0
        if (trimmed.any { it.isDigit() }) return 0
        if (containsAny(lower, jobTitleKeywords) || containsAny(lower, companyKeywords) || containsAny(lower, addressKeywords)) return 0
        val letters = trimmed.count { it.isLetter() }
        if (letters < 4) return 0
        var score = 40 + letters.coerceAtMost(12)
        // Allow 2-4 words for names
        if (Regex("^[A-Z][a-z]+(\\s+[A-Z][a-z]+){1,3}$").matches(trimmed)) score += 35
        if (Regex("^[A-Z][A-Za-z'.-]+(\\s+[A-Z][A-Za-z'.-]+){1,4}$").matches(trimmed)) score += 25
        if (trimmed == trimmed.uppercase()) score -= 10
        if (block.y <= blocks.minOfOrNull { it.y } ?: 0) score += 12
        if (block.y <= ((blocks.minByOrNull { it.y }?.y ?: 0) + 80)) score += 5
        if (block.y < phoneMinY && phoneMinY != Int.MAX_VALUE) score += 15
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
        if (containsAny(lower, addressKeywords)) score -= 20
        if (containsAny(lower, jobTitleKeywords)) score -= 20
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
