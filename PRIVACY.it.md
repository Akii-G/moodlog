# Informativa sulla privacy di Moodlog

*Read in English: [PRIVACY.md](PRIVACY.md)*

Ultimo aggiornamento: 21 novembre 2026. Vale per Moodlog per PC 2.11.0, Moodlog per iPhone 1.9.0 e Moodlog per Android 1.5.0, e per le versioni successive.

## In breve

- **Nessuno raccoglie i tuoi dati.** Moodlog non ha account, server, statistiche d'uso, pubblicità né tracciamento. Io, lo sviluppatore, non vedo mai niente di quello che scrivi.
- **I tuoi dati restano sui tuoi dispositivi:** il tuo PC e, se ne usi uno, il tuo telefono.
- **Viaggiano solo due cose, e solo se le attivi tu:** la sincronizzazione del telefono, direttamente al tuo PC sulla tua rete, cifrata; e un file di backup nel posto che scegli, come Google Drive o iCloud Drive.
- **Nessuna delle due app si collega a internet.**

## Chi fa Moodlog

Moodlog è un progetto personale gratuito e open source di Giosuè ([Akii-G su GitHub](https://github.com/Akii-G)). Non è un'azienda e non vende niente. Tutto il codice è pubblico in [questo repository](https://github.com/Akii-G/moodlog), quindi chiunque può verificare quello che dice questa pagina.

## Cosa salva Moodlog, e dove

Tutto quello che inserisci: umori e le loro note, tag, eventi (come un cambio di farmaco), sogni e, se lo attivi, i dati di salute dal tuo telefono.

- **Sul PC** sta in semplici file nella tua cartella dei dati: `~/.local/share/moodlog/`, se non ne hai scelta un'altra. I file sono `moods.csv`, `events.csv`, `dreams.csv`, `health.csv` e, se usi i farmaci da Salute di Apple, `medications.json`. I questionari che compili (PHQ-9, GAD-7) sono in `surveys.json`. Le impostazioni sono in `~/.config/moodlog/config.json`. Il token della sincronizzazione è in `sync.json`, e una riga sull'ultima sincronizzazione è in `sync-last.txt`.
- **Su iPhone e su Android** sta nello spazio privato dell'app, che le altre app non possono leggere.

Moodlog non cifra i file sul PC. Li proteggono il tuo account utente e, se l'hai attivata quando hai installato il sistema, la cifratura del disco. Se tieni la cartella dei dati in una cartella sincronizzata da un altro programma (per esempio Syncthing, Nextcloud o Dropbox), quel programma, e qualsiasi server usi, ne terrà una copia secondo la sua informativa.

Se il tuo iPhone fa il backup su iCloud o su un computer, il backup contiene i dati di Moodlog, come per ogni app. Di quei backup si occupa Apple. Lo stesso vale per il backup di Android sul tuo account Google, se è attivo: se ne occupa Google, cifrato con il blocco schermo se il telefono ne ha uno.

## Cosa esce dal tuo dispositivo

**Sincronizzazione del telefono (facoltativa).** Se la configuri, l'app sul telefono invia le tue voci al ricevitore sul tuo PC e riceve quelle del PC.
- Succede solo tra i tuoi dispositivi, sulla tua rete. Non passa mai da internet né da un mio server.
- Tutto è cifrato con AES-256-GCM, con una chiave ricavata dal tuo token. Il token stesso non viene mai inviato.
- Il ricevitore ascolta sulla porta 8765 del tuo PC e risponde solo alle richieste fatte con il tuo token.
- Se il tuo telefono Android non trova da solo un indirizzo come `pop-os.local`, Moodlog lo chiede alla tua rete locale (nello stesso modo in cui il PC annuncia il suo nome). Quella domanda non esce mai dalla tua rete Wi-Fi.
- Android chiama questo permesso "accesso completo alla rete": serve solo per il tuo PC.

**Dati di salute (facoltativi, disattivati all'inizio).** L'app per iPhone legge i dati di salute solo dopo che glielo permetti in Salute, e solo quando attivi "Invia a ogni sincronizzazione" o tocchi "Invia ora gli ultimi 7 giorni" (Impostazioni dell’app → Salute di Apple). Allora legge:
- i tuoi passi,
- la frequenza cardiaca a riposo,
- il sonno,

tutto degli ultimi 7 giorni, e li invia al tuo PC con la sincronizzazione. Moodlog non scrive mai in Salute.

**Farmaci (facoltativi, disattivati all'inizio, iPhone con iOS 26 o successivo).** Se attivi "Leggi i miei farmaci da Salute di Apple" (Impostazioni dell'app → Farmaci), Salute ti chiede quali farmaci Moodlog può leggere. Moodlog legge allora quei farmaci e se hai preso o saltato ogni dose, e li mostra in ogni giorno e nel resoconto per il medico. Quando invia i dati di salute al tuo PC, invia anche le dosi dei farmaci (tutta la storia la prima volta, poi gli ultimi 30 giorni). Moodlog non scrive mai in Salute e non suggerisce mai niente su un farmaco. Puoi cambiare quali farmaci può leggere nell'app Salute.

Su Android funziona allo stesso modo con Connessione Salute: l'app legge passi, frequenza cardiaca a riposo e sonno degli ultimi 7 giorni solo dopo che glielo permetti in Connessione Salute, e solo quando attivi "Invia a ogni sincronizzazione" o tocchi "Invia ora gli ultimi 7 giorni" (Impostazioni → Connessione Salute). Non scrive mai in Connessione Salute. I dati di salute servono solo a mostrarli accanto al tuo umore. Non sono mai usati per la pubblicità, non vengono mai venduti né dati a nessuno.

**Il Comando rapido di Apple (facoltativo, il vecchio modo di inviare i dati di salute).** I Comandi rapidi non possono cifrare. Se lo usi, i suoi dati e il token attraversano la rete in chiaro, quindi usalo solo su una rete di cui ti fidi. La sincronizzazione dell'app per iPhone è il modo cifrato.

**Backup (facoltativo).** Sui telefoni puoi scegliere un file o una cartella, per esempio in Google Drive o iCloud Drive. Moodlog ci tiene una copia di tutto dopo ogni modifica. Moodlog scrive solo il file; lo carica l'app Drive o iCloud Drive, secondo l'informativa di Google o di Apple. Puoi smettere quando vuoi in Impostazioni → Backup.

**Report ed esportazioni.** Il report PDF, il foglio di calcolo e il file CSV vengono salvati dove scegli tu. Cosa farne dopo (per esempio mandarli al tuo medico) lo decidi tu. Una nota spuntata come **Privata** non finisce mai nel report, anche quando includi le note; un sogno privato viene solo contato, e di cosa parlava non viene mai mostrato. Note e sogni privati restano nei tuoi dati, nei backup e nella sincronizzazione come tutto il resto.

**Promemoria e parola gentile.** Il promemoria giornaliero è una notifica creata sul tuo dispositivo; non viene inviato niente da nessuna parte. La parola gentile nei giorni difficili viene calcolata sul tuo dispositivo da ciò che hai registrato; non avvisa nessuno, e toccare una linea di ascolto apre solo il telefono o il browser.

**Link.** Alcuni pulsanti, come "Leggi l'informativa completa", aprono una pagina nel tuo browser. Succede solo quando li clicchi, e da lì valgono le regole del browser e del sito.

## Cosa scrive il ricevitore nel registro di sistema

Per ogni richiesta, il ricevitore scrive una riga nel registro di sistema del PC (il journal): l'ora, l'indirizzo locale del telefono e quale parte del ricevitore è stata chiamata (per esempio `/api/e/sync`). Non scrive mai le tue voci, i tuoi dati di salute né il tuo token.

## Il controllo è tuo

- **Vedere tutto:** i tuoi dati sono normali file CSV. Aprili da ☰ → Apri la cartella dei dati.
- **Portarli via:** puoi fare un backup o esportarli quando vuoi.
- **Cancellarli:** cancella una voce dall'app. Per cancellare tutto, elimina la cartella dei dati sul PC (e `~/.config/moodlog/`), ed elimina l'app dal telefono.
- **Smettere di sincronizzare:** cancella l'indirizzo del PC nelle Impostazioni dell'app sul telefono, oppure ferma il ricevitore con `systemctl --user disable --now moodlog-sync.service`. Clicca "Cambia token…" nella finestra Sincronizza telefono del PC per rendere inutile il vecchio token.

## Bambini

Moodlog non è pensato per i bambini, e non raccoglie niente da nessuno.

## Modifiche

Se Moodlog dovesse mai trattare i dati in modo diverso, questa pagina cambierà prima, e la modifica sarà elencata nelle novità dell'app. La data in alto indica sempre l'ultima versione.

## Domande

Apri una [segnalazione su GitHub](https://github.com/Akii-G/moodlog/issues).
