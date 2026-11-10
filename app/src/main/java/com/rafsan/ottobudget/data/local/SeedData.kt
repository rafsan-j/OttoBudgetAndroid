package com.rafsan.ottobudget.data.local

object SeedData {
    val defaultWallets = listOf(
        WalletEntity("w_bkash", "bKash", "LIQUID", 0.0, "Mobile Wallet", 0xFFE11D48),
        WalletEntity("w_card", "Bank Card", "CARDS", 0.0, "Debit / Credit", 0xFF1E293B),
        WalletEntity("w_cash", "Cash", "LIQUID", 0.0, "Physical Cash", 0xFFC8F904),
        WalletEntity("w_transit", "Transit Pass", "CARDS", 0.0, "Rapid / Metro Pass", 0xFF0284C7),
        WalletEntity("w_savings", "Savings", "CARDS", 0.0, "Reserve Vault", 0xFF059669)
    )

    val defaultCategories = listOf(
        // Expense Categories (Monochrome vector icon identifiers)
        CategoryEntity("cat_edu", "Education", "EXPENSE", 0xFF1E2329, "book"),
        CategoryEntity("cat_food", "Food & Dining", "EXPENSE", 0xFF1E2329, "coffee"),
        CategoryEntity("cat_groceries", "Groceries", "EXPENSE", 0xFF1E2329, "bag"),
        CategoryEntity("cat_shopping", "Shopping & Tech", "EXPENSE", 0xFF1E2329, "tech"),
        CategoryEntity("cat_transport", "Transport", "EXPENSE", 0xFF1E2329, "bus"),
        CategoryEntity("cat_utilities", "Utilities & Bills", "EXPENSE", 0xFF1E2329, "bolt"),
        CategoryEntity("cat_health", "Health & Care", "EXPENSE", 0xFF1E2329, "health"),
        CategoryEntity("cat_housing", "Housing & Rent", "EXPENSE", 0xFF1E2329, "home"),
        CategoryEntity("cat_other", "Other", "EXPENSE", 0xFF1E2329, "ledger"),
        // Income Categories
        CategoryEntity("cat_inc_salary", "Salary & Job", "INCOME", 0xFF0ECB81, "wallet"),
        CategoryEntity("cat_inc_freelance", "Freelance", "INCOME", 0xFF0ECB81, "tech"),
        CategoryEntity("cat_inc_tuition", "Tuition & Teaching", "INCOME", 0xFF0ECB81, "book"),
        CategoryEntity("cat_inc_family", "Family & Allowance", "INCOME", 0xFF0ECB81, "home"),
        CategoryEntity("cat_inc_other", "Gifts & Stipend", "INCOME", 0xFF0ECB81, "bolt")
    )

    private fun buildRules(category: String, kind: String, walletHint: String, words: List<String>): List<KeywordRuleEntity> {
        return words.map { kw ->
            val clean = kw.trim().lowercase()
            KeywordRuleEntity(
                keyword = clean,
                category = category,
                kind = kind,
                walletHint = walletHint,
                priority = if (clean.contains(" ")) 110 else 85
            )
        }
    }

    val defaultKeywordRules: List<KeywordRuleEntity> = buildList {
        addAll(buildRules("Transport", "EXPENSE", "Cash", listOf(
            "bus", "bus fare", "bus ticket", "train", "train ticket", "metro", "subway",
            "rapid pass", "transit pass", "mrt", "uber", "pathao", "taxi", "cab", "cng",
            "auto", "rickshaw", "leguna", "ride", "commute", "fuel", "petrol", "octane",
            "diesel", "gas", "parking", "toll", "ferry", "launch", "flight", "airfare",
            "bicycle", "bike", "scooter", "fare", "transport", "transportation"
        )))
        addAll(buildRules("Food & Dining", "EXPENSE", "Cash", listOf(
            "food", "coffee", "tea", "cha", "latte", "espresso", "cafe", "breakfast",
            "brunch", "lunch", "dinner", "supper", "restaurant", "bistro", "canteen",
            "buffet", "snacks", "snack", "burger", "pizza", "sandwich", "shawarma",
            "sub", "taco", "pasta", "noodles", "cup noodles", "ramen", "soup", "dumpling",
            "momo", "biryani", "kacchi", "khichuri", "fried rice", "tehari", "polao",
            "kebab", "grill", "chicken", "nuggets", "fries", "singara", "samosa", "puri",
            "dal puri", "paratha", "roti", "bakery", "pastry", "cake", "donut", "muffin",
            "biscuits", "cookies", "chips", "crackers", "sweets", "dessert", "ice cream",
            "ice-cream", "gelato", "chocolate", "chocolates", "kitkat", "candy", "drinks",
            "juice", "smoothie", "soda", "coke", "sprite", "pepsi", "water bottle", "fuchka"
        )))
        addAll(buildRules("Groceries", "EXPENSE", "Cash", listOf(
            "groceries", "grocery", "supermarket", "bazar", "market", "mart", "shwapno",
            "agora", "meena bazar", "unimart", "vegetables", "veggies", "fruits", "apple",
            "banana", "orange", "mango", "potato", "onion", "garlic", "ginger", "tomato",
            "milk", "dairy", "butter", "cheese", "yogurt", "curd", "egg", "eggs", "meat",
            "beef", "mutton", "poultry", "fish", "seafood", "rice", "puffed rice", "muri",
            "flour", "atta", "oil", "soybean", "soyabin", "mustard oil", "salt", "sugar",
            "spices", "lentils", "dal", "bread", "oats", "cereal", "honey", "jam"
        )))
        addAll(buildRules("Utilities & Bills", "EXPENSE", "bKash", listOf(
            "electricity", "electric bill", "power bill", "desco", "dpdc", "pdb", "palli bidyut",
            "water bill", "wasa", "gas bill", "titas", "lpg", "cylinder", "internet",
            "wifi", "broadband", "isp", "router", "mobile recharge", "recharge", "topup",
            "top-up", "mobile data", "data pack", "talktime", "prepaid", "postpaid",
            "phone bill", "grameenphone", "gp", "skitto", "robi", "airtel", "banglalink",
            "teletalk", "utility", "utilities", "bill", "garbage bill", "service charge",
            "subscription", "icloud", "google one", "netflix", "spotify"
        )))
        addAll(buildRules("Education", "EXPENSE", "bKash", listOf(
            "education", "book", "books", "textbook", "guidebook", "novel", "course",
            "online course", "class", "workshop", "bootcamp", "seminar", "tuition fee",
            "semester fee", "admission", "admission fee", "application", "application fee",
            "registration", "exam", "exam fee", "test fee", "coaching", "udvash", "unmesh",
            "university", "college", "school", "department", "library", "stationery",
            "notebook", "khata", "paper", "a4", "pen", "pencil", "marker", "highlighter",
            "eraser", "sharpener", "ruler", "calculator", "folder", "file", "binder",
            "print", "printing", "photocopy", "xerox", "lamination", "binding", "lab"
        )))
        addAll(buildRules("Health & Care", "EXPENSE", "Cash", listOf(
            "medicine", "meds", "tablet", "capsule", "syrup", "inhaler", "drops", "pharmacy",
            "dispensary", "drugstore", "doctor", "physician", "consultation", "hospital",
            "clinic", "diagnostic", "lab test", "blood test", "xray", "x-ray", "ecg",
            "refraction", "eye test", "dental", "dentist", "first aid", "bandage", "vitamin",
            "supplement", "gym", "fitness", "workout", "yoga", "haircut", "hair cut",
            "barber", "salon", "saloon", "trim", "shave", "shaving", "shaving foam",
            "razor", "blade", "soap", "body wash", "shower gel", "shampoo", "conditioner",
            "toothpaste", "toothbrush", "mouthwash", "facewash", "moisturizer", "lotion",
            "sunscreen", "perfume", "deodorant", "body spray", "cotton bud", "tissue"
        )))
        addAll(buildRules("Shopping & Tech", "EXPENSE", "Bank Card", listOf(
            "shopping", "clothing", "clothes", "apparel", "shirt", "t-shirt", "tshirt",
            "polo", "panjabi", "kurta", "pant", "trousers", "jeans", "chinos", "shorts",
            "jacket", "hoodie", "sweater", "cardigan", "turtle neck", "suit", "blazer",
            "formal dress", "dress", "saree", "salwar", "shoes", "sneakers", "loafers",
            "sandals", "slippers", "socks", "belt", "wallet", "tie", "watch", "glasses",
            "sunglasses", "spectacles", "bag", "backpack", "luggage", "umbrella", "tech",
            "electronics", "phone", "smartphone", "laptop", "pc", "monitor", "keyboard",
            "mouse", "earbuds", "airpods", "headphones", "headset", "speaker", "charger",
            "adapter", "cable", "usb", "powerbank", "memory card", "ssd", "pendrive",
            "phone case", "cover", "screen protector", "camera protector", "phone stand"
        )))
        addAll(buildRules("Housing & Rent", "EXPENSE", "Cash", listOf(
            "rent", "house rent", "hostel", "mess", "dorm", "hall fee", "seat rent",
            "maintenance", "repair", "plumber", "electrician", "carpenter", "ac servicing",
            "laundry", "dry clean", "iron", "ironing", "detergent", "washing powder",
            "dishwash", "floor cleaner", "broom", "mop", "bucket", "furniture", "chair",
            "table", "desk", "mattress", "pillow", "bedsheet", "curtain", "light bulb",
            "fan", "lock", "key", "super glue", "hardware", "mosquito", "good knight", "coil"
        )))
        addAll(buildRules("Other", "EXPENSE", "Cash", listOf(
            "donation", "charity", "mosque", "zakat", "sadakah", "gift", "present",
            "tip", "fine", "penalty", "bank fee", "card fee", "excise duty", "tax",
            "postage", "courier", "delivery", "pathao parcel", "steadfast", "redx", "misc"
        )))

        // INCOME MAGIC WORDS
        addAll(buildRules("Salary & Job", "INCOME", "Bank Card", listOf(
            "salary", "paycheck", "wage", "wages", "part-time", "part time", "job",
            "internship", "stipend", "bonus", "festival bonus", "overtime", "commission",
            "incentive", "payout", "payroll"
        )))
        addAll(buildRules("Freelance", "INCOME", "bKash", listOf(
            "freelance", "freelancing", "upwork", "fiverr", "client", "project",
            "contract", "consulting", "design gig", "dev project", "milestone", "invoice",
            "bounty", "hackathon", "royalty", "youtube", "adsense", "affiliate"
        )))
        addAll(buildRules("Tuition & Teaching", "INCOME", "Cash", listOf(
            "tuition", "tutoring", "student", "students", "batch", "coaching class",
            "teaching", "lecture", "mentoring", "exam script", "honorarium"
        )))
        addAll(buildRules("Family & Allowance", "INCOME", "bKash", listOf(
            "allowance", "pocket money", "home", "from home", "family", "parents",
            "father", "mother", "abbu", "ammu", "brother", "sister", "uncle", "aunty",
            "monthly support", "living allowance"
        )))
        addAll(buildRules("Gifts & Stipend", "INCOME", "Cash", listOf(
            "scholarship", "grant", "bursary", "fellowship", "prize", "award", "gift",
            "salami", "eid salami", "cashback", "refund", "reimbursement", "dividend",
            "interest", "profit"
        )))
    }
}
