package com.minimalpairs.app.data.catalog

import com.minimalpairs.app.core.model.ComicOutcome
import com.minimalpairs.app.core.model.ComicStory

object MeaningTrapCatalog {

    val stories: List<ComicStory> = listOf(
        ComicStory(
            id = "sheep_vs_ship",
            contrastId = "i_vs_I",
            title = "The Farmer's Fleet",
            premise = "You are visiting a cozy countryside petting farm to adopt a fluffy companion.",
            intendedWord = "sheep",
            intendedIpa = "ʃ iː p",
            trapWord = "ship",
            trapIpa = "ʃ ɪ p",
            promptCharacter = "Old Farmer Giles",
            promptDialogue = "Welcome to Giles' Farm! What gentle creature would you like to adopt today?",
            promptInstruction = "Ask for a sheep: 'Can I have that sheep, please?'",
            targetPhrase = "Can I have that sheep, please?",
            targetPhraseIpa = "k ə n aɪ h æ v ð æ t ʃ iː p p l iː z",
            targetPhonemeIndex = 11,
            intendedOutcome = ComicOutcome(
                illustrationEmoji = "🐑 🏡 ✨",
                soundEffect = "BAAA! ❤️",
                dialogue = "Splendid choice! Little Barnaby here will graze happily in your meadow.",
                outcomeDescription = "Success! Perfect /iː/ pronunciation. You adopted a happy sheep."
            ),
            trapOutcome = ComicOutcome(
                illustrationEmoji = "🚢 💥 🚜 😱",
                soundEffect = "HOOOORN! CRUNCH!",
                dialogue = "A SHIP?! Good heavens, where am I going to dock an ocean freighter on dry pasture?!",
                outcomeDescription = "Trap triggered! /ɪ/ was detected instead of /iː/. A 50,000-ton cargo vessel demolished the cornfield."
            ),
            semanticTakeaway = "The difference between close front tense /iː/ and lax /ɪ/ is the difference between a 40kg barnyard animal and a 50,000-ton nautical vessel!"
        ),
        ComicStory(
            id = "pen_vs_pan",
            contrastId = "ae_vs_e",
            title = "The Exam Frying Trap",
            premise = "You sit down at the university examination hall, but your writing instrument is missing.",
            intendedWord = "pen",
            intendedIpa = "p e n",
            trapWord = "pan",
            trapIpa = "p æ n",
            promptCharacter = "Strict Professor Smith",
            promptDialogue = "The exam starts in two minutes. All students must have an ink utensil ready.",
            promptInstruction = "Ask the proctor: 'Excuse me, could you lend me a pen?'",
            targetPhrase = "Excuse me, could you lend me a pen?",
            targetPhraseIpa = "ɪ k s k j uː z m iː k ʊ d j uː l e n d m iː ə p e n",
            targetPhonemeIndex = 22,
            intendedOutcome = ComicOutcome(
                illustrationEmoji = "🖊️ 📝 🎓",
                soundEffect = "CLICK!",
                dialogue = "Certainly, here is a black ballpoint pen. Best of luck on your linguistics paper!",
                outcomeDescription = "Success! Mid-front /e/ clearly produced. You passed the exam."
            ),
            trapOutcome = ComicOutcome(
                illustrationEmoji = "🍳 🔥 🥓 😳",
                soundEffect = "SIZZLE! CLANG!",
                dialogue = "A PAN?! Why on earth would you need a heavy cast-iron skillet for a written exam?!",
                outcomeDescription = "Trap triggered! Open /æ/ produced instead of /e/. The professor slams a frying pan on your desk."
            ),
            semanticTakeaway = "Failing to narrow your jaw drop from open /æ/ (pan) to mid-front /e/ (pen) turns writing an essay into frying bacon!"
        ),
        ComicStory(
            id = "heart_vs_hut",
            contrastId = "a_vs_uh",
            title = "The Valentine Shelter",
            premise = "You are writing a romantic love poem to your sweetheart on Valentine's Day.",
            intendedWord = "heart",
            intendedIpa = "h ɑː t",
            trapWord = "hut",
            trapIpa = "h ʌ t",
            promptCharacter = "Cupid's Florist",
            promptDialogue = "What romantic message would you like engraved across the gold pendant?",
            promptInstruction = "Dictate: 'You hold the key to my heart.'",
            targetPhrase = "You hold the key to my heart.",
            targetPhraseIpa = "j uː h əʊ l d ð ə k iː t ə m aɪ h ɑː t",
            targetPhonemeIndex = 15,
            intendedOutcome = ComicOutcome(
                illustrationEmoji = "❤️ 🌹 💍",
                soundEffect = "Awww! 💕",
                dialogue = "How utterly poetic! 'Key to my heart' is engraved in fine italic script.",
                outcomeDescription = "Success! Deep open back /ɑː/ lengthened properly."
            ),
            trapOutcome = ComicOutcome(
                illustrationEmoji = "🛖 🔨 🪵 🤨",
                soundEffect = "THUD! THUMP!",
                dialogue = "'Key to my HUT'?! Are you locking your sweetheart into a wooden shed in the woods?!",
                outcomeDescription = "Trap triggered! Short central /ʌ/ produced. The florist engraves a dilapidated mud hut."
            ),
            semanticTakeaway = "Open back /ɑː/ requires long duration and tongue retraction; shortening it into /ʌ/ turns romance into a shed!"
        ),
        ComicStory(
            id = "fool_vs_full",
            contrastId = "u_vs_uu",
            title = "The Royal Banquet",
            premise = "The Emperor's grand feast is ending and the waiter offers you a fifth slice of roast beef.",
            intendedWord = "full",
            intendedIpa = "f ʊ l",
            trapWord = "fool",
            trapIpa = "f uː l",
            promptCharacter = "The Emperor's Butler",
            promptDialogue = "Would your lordship care for another roasted boar tenderloin?",
            promptInstruction = "Decline politely: 'No thank you, I am completely full.'",
            targetPhrase = "No thank you, I am completely full.",
            targetPhraseIpa = "n əʊ θ æ ŋ k j uː aɪ æ m k ə m p l iː t l i f ʊ l",
            targetPhonemeIndex = 21,
            intendedOutcome = ComicOutcome(
                illustrationEmoji = "🍷 🍽️ 👑",
                soundEffect = "CLINK!",
                dialogue = "Very good, my lord. We shall bring the digestifs shortly.",
                outcomeDescription = "Success! Lax near-close near-back /ʊ/ politely declined the dish."
            ),
            trapOutcome = ComicOutcome(
                illustrationEmoji = "🃏 🎪 🤹 😡",
                soundEffect = "GASP!",
                dialogue = "'You are completely FOOL'?! Did you just insult his Majesty's court jester?!",
                outcomeDescription = "Trap triggered! Tense close back /uː/ produced. Guards escort you to the dungeon."
            ),
            semanticTakeaway = "Tensing lips and raising tongue to /uː/ turns satiation ('full') into an insult ('fool')!"
        )
    )

    fun getStoryById(id: String): ComicStory? = stories.firstOrNull { it.id == id }
    fun getStoryForContrast(contrastId: String): ComicStory? = stories.firstOrNull { it.contrastId == contrastId }
}
