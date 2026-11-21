package io.github.akiig.moodlog

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One answered questionnaire (PHQ-9 or GAD-7). Synced and backed up like moods: the newest
 * change wins, deletions are kept as tombstones of type "survey". The same as the PC and iPhone.
 */
data class Survey(
    val id: String,
    val date: String,
    val time: String,
    val kind: String,          // "phq9" or "gad7"
    val answers: List<Int>,    // 0-3 for each question, in order
    val difficulty: Int,       // -1 = not answered, 0-3
    val updated: String,
    val private: Boolean = false,
) {
    val score get() = answers.sum()
    val maxScore get() = 3 * answers.size
    val name get() = SurveyKind.of(kind)?.title ?: kind

    /** PHQ-9 question 9 answered above "Not at all": Moodlog answers with kind words and
     *  helplines. It never tells or alerts anyone. */
    val worries get() = kind == "phq9" && answers.size == 9 && answers[8] > 0

    val isValid: Boolean
        get() {
            val k = SurveyKind.of(kind) ?: return false
            return id.isNotEmpty() && Day.date(date) != null && answers.size == k.size &&
                answers.all { it in 0..3 } && difficulty in -1..3
        }

    fun json(): JSONObject = JSONObject().put("id", id).put("date", date).put("time", time).put("kind", kind)
        .put("answers", JSONArray(answers)).put("difficulty", difficulty).put("updated", updated)
        .also { if (private) it.put("private", true) }

    companion object {
        fun from(o: JSONObject): Survey {
            val arr = o.optJSONArray("answers") ?: JSONArray()
            // a wrong type (true, 1.5, "2") becomes -1, so the questionnaire is dropped as not valid
            val answers = (0 until arr.length()).map { i -> (arr.opt(i) as? Int) ?: -1 }
            return Survey(o.optString("id"), o.optString("date"), o.optString("time"), o.optString("kind"),
                answers, o.optInt("difficulty", -1), o.optString("updated"), o.optBoolean("private", false))
        }
    }
}

enum class SurveyKind(val raw: String, val title: String, val size: Int) {
    PHQ9("phq9", "PHQ-9", 9), GAD7("gad7", "GAD-7", 7);

    val maxScore get() = 3 * size
    val text get() = SurveyText.current.forms.getValue(this)

    companion object {
        fun of(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * The official words, as published on phqscreeners.com (checked 21 November 2026). Each
 * language has its own validated wording, so these are not translated with tr().
 * "Developed by Drs. Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues, with an
 * educational grant from Pfizer Inc. No permission required to reproduce, translate, display or
 * distribute." Moodlog shows raw scores only: no severity words, nothing like a diagnosis.
 */
class SurveyText(
    val forms: Map<SurveyKind, Form>,
    val difficulty: String,
    val difficultyOptions: List<String>,
    val credit: String,
) {
    class Form(val about: String, val question: String, val options: List<String>, val items: List<String>)

    companion object {
        val current: SurveyText get() = if (appLocale().language == "it") italian else english

        val english = SurveyText(
            mapOf(
                SurveyKind.PHQ9 to Form(
                    "Patient Health Questionnaire",
                    "Over the last 2 weeks, how often have you been bothered by any of the following problems?",
                    listOf("Not at all", "Several days", "More than half the days", "Nearly every day"),
                    listOf(
                        "Little interest or pleasure in doing things",
                        "Feeling down, depressed, or hopeless",
                        "Trouble falling or staying asleep, or sleeping too much",
                        "Feeling tired or having little energy",
                        "Poor appetite or overeating",
                        "Feeling bad about yourself — or that you are a failure or have let yourself or your family down",
                        "Trouble concentrating on things, such as reading the newspaper or watching television",
                        "Moving or speaking so slowly that other people could have noticed? Or the opposite — being so fidgety or restless that you have been moving around a lot more than usual",
                        "Thoughts that you would be better off dead or of hurting yourself in some way",
                    )),
                SurveyKind.GAD7 to Form(
                    "Generalized Anxiety Disorder 7-item scale",
                    "Over the last 2 weeks, how often have you been bothered by the following problems?",
                    listOf("Not at all", "Several days", "More than half the days", "Nearly every day"),
                    listOf(
                        "Feeling nervous, anxious or on edge",
                        "Not being able to stop or control worrying",
                        "Worrying too much about different things",
                        "Trouble relaxing",
                        "Being so restless that it is hard to sit still",
                        "Becoming easily annoyed or irritable",
                        "Feeling afraid as if something awful might happen",
                    )),
            ),
            "If you checked off any problems, how difficult have these problems made it for you to do your work, take care of things at home, or get along with other people?",
            listOf("Not difficult at all", "Somewhat difficult", "Very difficult", "Extremely difficult"),
            "Developed by Drs. Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues, with an educational grant from Pfizer Inc. No permission required to reproduce, translate, display or distribute.")

        val italian = SurveyText(
            mapOf(
                SurveyKind.PHQ9 to Form(
                    "Questionario sulla salute del/della paziente",
                    "Nelle ultime 2 settimane, con quale frequenza le ha dato fastidio ciascuno dei seguenti problemi?",
                    listOf("Mai", "Alcuni giorni", "Per più della metà dei giorni", "Quasi ogni giorno"),
                    listOf(
                        "Scarso interesse o piacere nel fare le cose",
                        "Sentirsi giù, triste o disperato/a",
                        "Problemi ad addormentarsi o a dormire tutta la notte senza svegliarsi, o a dormire troppo",
                        "Sentirsi stanco/a o avere poca energia",
                        "Scarso appetito o mangiare troppo",
                        "Avere una scarsa opinione di sé, o sentirsi un/una fallito/a o aver deluso se stesso/a o i propri familiari",
                        "Difficoltà a concentrarsi su qualcosa, per esempio leggere il giornale o guardare la televisione",
                        "Muoversi o parlare così lentamente da poter essere notato/a da altre persone. O, al contrario, essere così irrequieto/a da muoversi molto più del solito",
                        "Pensare che sarebbe meglio morire o farsi del male in un modo o nell'altro",
                    )),
                SurveyKind.GAD7 to Form(
                    "Questionario sul disturbo d'ansia generalizzato",
                    "Nelle ultime 2 settimane, con quale frequenza le ha dato fastidio ciascuno dei seguenti problemi?",
                    listOf("Mai", "Alcuni giorni", "Per oltre la metà dei giorni", "Quasi ogni giorno"),
                    listOf(
                        "Sentirsi nervoso/a, ansioso/a o teso/a",
                        "Non riuscire a smettere di preoccuparsi o a tenere sotto controllo le preoccupazioni",
                        "Preoccuparsi troppo per varie cose",
                        "Avere difficoltà a rilassarsi",
                        "Essere talmente irrequieto/a da far fatica a stare seduto/a fermo/a",
                        "Infastidirsi o irritarsi facilmente",
                        "Avere paura che possa succedere qualcosa di terribile",
                    )),
            ),
            "Se ha fatto una crocetta su uno qualsiasi di questi problemi, quanto questi problemi le hanno reso difficile fare il suo lavoro, occuparsi delle sue cose a casa o avere buoni rapporti con gli altri?",
            listOf("Per niente difficile", "Abbastanza difficile", "Molto difficile", "Estremamente difficile"),
            "Elaborato dai dottori Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke e colleghi, con un finanziamento da parte della Pfizer Inc. Non è richiesto un permesso per la riproduzione, traduzione, visualizzazione o distribuzione.")
    }
}

/**
 * The two-week reminder: one notification 14 days after the last questionnaire, at 19:00. If
 * that day has passed and nothing new was answered, once a week after that. Nothing is planned
 * before the first questionnaire. Its own alarm and notification, apart from the daily reminder.
 */
object SurveyReminder {
    const val CHANNEL = "questionnaires"
    const val ACTION = "io.github.akiig.moodlog.REMIND_SURVEY"
    const val OPEN_SURVEYS = "open_surveys"
    const val EVERY_DAYS = 14L
    const val HOUR = 19

    fun next(lastDay: String?, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val last = lastDay?.let { Day.date(it) } ?: return null
        var at = last.plusDays(EVERY_DAYS).atTime(HOUR, 0)
        while (!at.isAfter(now)) at = at.plusDays(7)
        return at
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 3, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun lastDay(context: Context): String? =
        AppStore(File(context.filesDir, "moodlog.json")).surveys.maxOfOrNull { it.date }

    fun isOn(prefs: Prefs) = prefs.questionnaires && prefs.surveyRemind

    /** (Re)plans the next reminder, or cancels it. */
    fun schedule(context: Context) {
        val prefs = Prefs(context)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pending(context))
        if (!isOn(prefs)) return
        val at = next(lastDay(context)) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, 30 * 60_000L, pending(context))
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, tr("Questionnaires"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = tr("A gentle reminder two weeks after your last questionnaire.")
            setShowBadge(false)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    fun show(context: Context) {
        if (!Reminder.canNotify(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(context, 4,
            Intent(context, MainActivity::class.java).putExtra(OPEN_SURVEYS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Two weeks since your last questionnaires"))
            .setContentText(tr("Answering PHQ-9 and GAD-7 again takes about two minutes, and helps your doctor see how things are going."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                tr("Answering PHQ-9 and GAD-7 again takes about two minutes, and helps your doctor see how things are going.")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(2, n)
        } catch (e: SecurityException) {
            // notifications were turned off in the meantime
        }
    }

    fun onAlarm(context: Context) {
        if (isOn(Prefs(context))) show(context)
        schedule(context)
    }
}
