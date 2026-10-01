package sukun.minimalist.app.launcher.com.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

enum class HomeAppCategory {
    SOCIAL,
    FINANCE,
    COMMUNICATION,
    GAMES,
    PRODUCTIVITY,
}

object HomeAppCategories {

    private val socialPackages = setOf(
        "com.instagram.android",
        "com.instagram.lite",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android",
        "com.x.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.pinterest",
        "com.tumblr",
        "com.instagram.barcelona",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.bereal.ft",
        "com.vkontakte.android",
        "com.ss.android.ugc.aweme",
    )

    private val financePackages = setOf(
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "com.revolut.revolut",
        "com.wise.android",
        "com.robinhood.android",
        "com.coinbase.android",
        "org.toshi",
        "com.binance.dev",
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "com.infonow.bofa",
        "com.konylabs.capitalone",
        "com.usbank.mobilebanking",
        "com.citi.citimobile",
        "com.americanexpress.android.acctsvcs.us",
        "com.discoverfinancial.mobile",
        "com.klarna.mobile",
        "com.starlingbank.android",
        "com.grppl.android.shell.CMBlloydsTSB73",
        "com.rbs.mobile.android.natwest",
        "com.barclays.android.barclaysmobilebanking",
        "com.htsu.hsbcpersonalbanking",
        "com.konylabs.cbplpat",
        "com.sbi.lotusintouch",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.axis.mobile",
        "com.msf.kbank.mobile",
        "com.intuit.mint",
        "com.mint",
        "com.ynab.androids2",
        "com.splitwise.android",
        "com.creditkarma.mobile",
        "com.hrblock.tax.apps.android",
        "com.intuit.turbotax.mobile",
        "com.stripe.android.dashboard",
    )

    private val communicationPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.google.android.gm",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.messaging",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.viber.voip",
        "com.skype.raider",
        "com.discord",
        "jp.naver.line.android",
        "com.tencent.mm",
        "com.imo.android.imoim",
        "com.google.android.apps.tachyon",
        "us.zoom.videomeetings",
        "com.microsoft.teams",
        "com.Slack",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "ch.protonmail.android",
        "com.google.android.contacts",
        "com.android.contacts",
        "com.samsung.android.app.contacts",
    )

    private val gamePackages = setOf(
        "com.king.candycrushsaga",
        "com.king.candycrushsodasaga",
        "com.supercell.clashofclans",
        "com.supercell.brawlstars",
        "com.supercell.clashroyale",
        "com.roblox.client",
        "com.mojang.minecraftpe",
        "com.activision.callofduty.shooter",
        "com.epicgames.fortnite",
        "com.dts.freefireth",
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.pubg.newstate",
        "com.nianticlabs.pokemongo",
        "com.kiloo.subwaysurf",
        "com.miniclip.eightballpool",
        "com.innersloth.spacemafia",
        "com.playrix.homescapes",
        "com.playrix.gardenscapes",
        "com.ea.gp.fifamobile",
        "com.nintendo.zara",
        "com.miHoYo.GenshinImpact",
    )

    private val productivityPackages = setOf(
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides",
        "com.google.android.keep",
        "com.google.android.calendar",
        "com.google.android.apps.tasks",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.microsoft.todos",
        "com.microsoft.office.officehubrow",
        "com.notion.id",
        "com.todoist",
        "com.evernote",
        "com.anydo",
        "com.ticktick.task",
        "com.habitrpg.android.habitica",
        "com.google.android.apps.maps",
        "com.google.android.gm.lite",
        "com.dropbox.android",
        "com.adobe.reader",
        "org.mozilla.firefox",
        "com.android.chrome",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
    )

    @Volatile
    private var cachedCommunicationDefaults: Set<String>? = null

    fun matches(context: Context, packageName: String, category: HomeAppCategory): Boolean {
        return classify(context, packageName) == category
    }

    fun classify(context: Context, packageName: String): HomeAppCategory? {
        if (packageName.isBlank()) return null
        val androidCategory = androidCategory(context, packageName)
        val isGame = packageName in gamePackages ||
            androidCategory == ApplicationInfo.CATEGORY_GAME ||
            isLegacyGame(context, packageName)
        if (isGame) return HomeAppCategory.GAMES

        if (packageName in communicationPackages ||
            packageName in communicationDefaults(context)
        ) {
            return HomeAppCategory.COMMUNICATION
        }

        if (packageName in socialPackages || androidCategory == ApplicationInfo.CATEGORY_SOCIAL) {
            return HomeAppCategory.SOCIAL
        }

        if (packageName in financePackages) return HomeAppCategory.FINANCE

        if (packageName in productivityPackages ||
            androidCategory == ApplicationInfo.CATEGORY_PRODUCTIVITY
        ) {
            return HomeAppCategory.PRODUCTIVITY
        }
        return null
    }

    private fun androidCategory(context: Context, packageName: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return ApplicationInfo.CATEGORY_UNDEFINED
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
    }

    @Suppress("DEPRECATION")
    private fun isLegacyGame(context: Context, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return false
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun communicationDefaults(context: Context): Set<String> {
        cachedCommunicationDefaults?.let { return it }
        val found = mutableSetOf<String>()
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        )
        intents.forEach { intent ->
            try {
                context.packageManager
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
                    ?.packageName
                    ?.let { found += it }
            } catch (_: Exception) {
            }
        }
        cachedCommunicationDefaults = found
        return found
    }
}
