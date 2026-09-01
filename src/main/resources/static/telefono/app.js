(function () {
    "use strict";

    var app = document.getElementById("app");
    var codice = new URLSearchParams(window.location.search).get("codice");

    if (!codice) {
        app.innerHTML = '<div class="errore">Codice partecipante mancante nell\'URL</div>';
        return;
    }

    var PULSANTI_RAPIDI = [1, 2, 5, 10];

    var listone = null;
    var snapshot = null;

    // Il DOM NON viene ricostruito a ogni snapshot: si aggiornano in posto i soli
    // nodi che cambiano (offerta, offerente, crediti, stato). Il campo importo e i
    // pulsanti rapidi restano gli stessi elementi finche' non cambia il lotto, cosi'
    // il focus e il testo digitato non si perdono quando arriva un'offerta altrui.
    // (Requisito che vale doppio col countdown del Gruppo 3, che aggiornera' ogni secondo.)
    var vista = null;            // 'attesa' | 'codice-assente' | 'asta'
    var statoAreaLotto = null;   // idLotto con skeleton in DOM, 'vuoto', oppure null

    var RUOLI = ["P", "D", "C", "A"];
    // Firma dell'ultimo contenuto disegnato per ogni reparto della rosa: finche' non
    // cambia, i chip dei calciatori restano gli stessi nodi e non si ridisegna nulla.
    var firmeRosa = {};

    // Countdown scalato localmente solo per l'animazione: il tempo autorevole e' quello
    // del server. A ogni snapshot ci si riallinea fissando l'istante di scadenza locale
    // (adesso + secondiResidui); tra uno snapshot e l'altro si mostra ceil((scadenza -
    // adesso)/1000), stessa semantica del server. Il refresh e' piu' fitto di un secondo
    // (ogni CADENZA_COUNTDOWN ms) cosi' il numero cambia a ridosso del secondo reale e la
    // coda 2 -> 1 -> scaduto non ha beat di troppo. Si aggiorna in posto il solo nodo
    // #countdown (mai l'input importo, che non deve perdere focus ne' testo).
    var CADENZA_COUNTDOWN = 250;
    var statoLottoCorrente = null;    // stato del lotto per decidere se il countdown scorre
    var istanteScadenzaLocale = 0;    // epoch ms stimato di scadenza, per la sola animazione

    // Ricerca sul listone: tutta locale. Il listone arriva una volta con la GET
    // /api/listone e non cambia piu'; lo stato "gia' preso" invece arriva dallo
    // snapshot (calciatoriAssegnati), quindi i risultati si riclassificano da soli
    // a ogni aggiudicazione, anche con la ricerca aperta a schermo.
    // Il listone reale supera le 500 righe: si renderizzano al massimo MAX_LIBERI
    // risultati liberi e MAX_NON_DISPONIBILI tra presi e fuori lista.
    var MAX_LIBERI = 40;
    var MAX_NON_DISPONIBILI = 15;
    var filtroNome = "";
    var filtroRuolo = "";          // "" = tutti i ruoli
    var firmaRicerca = null;       // ultimo HTML disegnato nei risultati, per non ridisegnarlo uguale

    function el(id) {
        return document.getElementById(id);
    }

    function esc(s) {
        var d = document.createElement("div");
        d.textContent = s == null ? "" : String(s);
        return d.innerHTML;
    }

    function trovaCalciatore(id) {
        if (!listone) return null;
        for (var i = 0; i < listone.length; i++) {
            if (listone[i].id === id) return listone[i];
        }
        return null;
    }

    function trovaPartecipante(cod) {
        if (!snapshot) return null;
        for (var i = 0; i < snapshot.partecipanti.length; i++) {
            if (snapshot.partecipanti[i].codice === cod) return snapshot.partecipanti[i];
        }
        return null;
    }

    // --- Viste semplici: ricostruite solo quando si entra nella vista, non a ogni snapshot ---

    function mostraAttesa() {
        if (vista === "attesa") return;
        vista = "attesa";
        statoAreaLotto = null;
        app.innerHTML =
            '<div class="intestazione"><h1>Fantasta</h1></div>' +
            '<div class="stato-attesa">Connessione in corso…</div>';
    }

    function mostraCodiceAssente() {
        if (vista === "codice-assente") return;
        vista = "codice-assente";
        statoAreaLotto = null;
        app.innerHTML = '<div class="errore">Codice ' + esc(codice) + ' non trovato nell\'asta</div>';
    }

    // Layout base dell'asta (intestazione, contenitore del lotto, rosa): costruito una
    // sola volta. Anche i quattro reparti della rosa sono nodi fissi: a ogni snapshot si
    // riscrivono solo conteggio e chip, e solo dei reparti davvero cambiati.
    function costruisciLayoutAsta() {
        vista = "asta";
        statoAreaLotto = null;
        firmeRosa = {};
        firmaRicerca = null;

        var reparti = "";
        for (var i = 0; i < RUOLI.length; i++) {
            var r = RUOLI[i];
            reparti +=
                '<div class="reparto">' +
                '<span class="ruolo ruolo-' + r + '">' + r + '</span>' +
                '<span class="reparto-conteggio" id="rosa-conteggio-' + r + '"></span>' +
                '<div class="reparto-calciatori" id="rosa-calciatori-' + r + '"></div>' +
                '</div>';
        }

        app.innerHTML =
            '<div class="intestazione">' +
            '<h1 id="p-nome"></h1>' +
            '<div class="codice" id="p-crediti"></div>' +
            '</div>' +
            '<div id="area-lotto"></div>' +
            '<div class="rosa">' +
            '<div class="rosa-testa">' +
            '<span>La tua rosa</span>' +
            '<span class="rosa-spesa" id="rosa-spesa"></span>' +
            '</div>' +
            reparti +
            '</div>' +
            '<div class="ricerca">' +
            '<div class="ricerca-testa">Cerca un calciatore</div>' +
            '<input id="ricerca-nome" type="search" autocomplete="off"' +
            ' autocorrect="off" autocapitalize="off" placeholder="Nome calciatore">' +
            '<div class="ricerca-ruoli" id="ricerca-ruoli"></div>' +
            '<div class="ricerca-risultati" id="ricerca-risultati"></div>' +
            '</div>';

        costruisciRicerca();
    }

    // Campo nome e pulsanti ruolo: creati una volta sola e mai sostituiti, cosi' il
    // testo digitato e il focus sopravvivono a snapshot e countdown. I filtri vivono
    // in filtroNome/filtroRuolo, quindi si ripristinano anche se il layout si ricostruisce.
    function costruisciRicerca() {
        var input = el("ricerca-nome");
        input.value = filtroNome;
        input.addEventListener("input", function () {
            filtroNome = this.value;
            aggiornaRicerca();
        });

        var contenitore = el("ricerca-ruoli");
        var scelte = [["", "Tutti"], ["P", "P"], ["D", "D"], ["C", "C"], ["A", "A"]];
        for (var i = 0; i < scelte.length; i++) {
            var btn = document.createElement("button");
            btn.className = "btn-ruolo" + (scelte[i][0] === filtroRuolo ? " attivo" : "");
            btn.setAttribute("data-ruolo", scelte[i][0]);
            btn.textContent = scelte[i][1];
            btn.addEventListener("click", function () {
                filtroRuolo = this.getAttribute("data-ruolo");
                var tutti = el("ricerca-ruoli").querySelectorAll(".btn-ruolo");
                for (var j = 0; j < tutti.length; j++) {
                    var suo = tutti[j].getAttribute("data-ruolo") === filtroRuolo;
                    tutti[j].className = "btn-ruolo" + (suo ? " attivo" : "");
                }
                aggiornaRicerca();
            });
            contenitore.appendChild(btn);
        }
    }

    // Skeleton del lotto: costruito una volta per idLotto. Qui vivono l'input e i
    // pulsanti rapidi, che non vanno ricreati finche' resta lo stesso lotto.
    function costruisciLottoSkeleton(idLotto) {
        el("area-lotto").innerHTML =
            '<div class="lotto">' +
            '<div class="lotto-testa">' +
            '<span class="lotto-nome" id="lotto-nome"></span>' +
            '<span class="ruolo" id="lotto-ruolo"></span>' +
            '<span class="countdown" id="countdown"></span>' +
            '</div>' +
            '<div class="lotto-dettagli" id="lotto-dettagli"></div>' +
            '<div class="lotto-offerta" id="area-offerta"></div>' +
            '<div class="area-stato" id="area-stato"></div>' +
            '<div class="buzzer-rapidi" id="buzzer-rapidi"></div>' +
            '<div class="buzzer-libero">' +
            '<input id="importo-libero" type="number" inputmode="numeric" min="1" step="1" placeholder="Importo">' +
            '<button id="btn-offri">Offri</button>' +
            '</div>' +
            '<div class="messaggio-area" id="area-messaggio"></div>' +
            '</div>';

        var contenitore = el("buzzer-rapidi");
        for (var i = 0; i < PULSANTI_RAPIDI.length; i++) {
            var incremento = PULSANTI_RAPIDI[i];
            var btn = document.createElement("button");
            btn.className = "btn-rapido";
            btn.setAttribute("data-incremento", incremento);
            btn.innerHTML = "+" + incremento + '<span class="assoluto"></span>';
            // L'importo assoluto (data-importo) viene aggiornato in posto da aggiornaOfferta;
            // il click lo legge al momento, quindi il gestore si aggancia una volta sola.
            btn.addEventListener("click", function () {
                inviaOfferta(idLotto, parseInt(this.getAttribute("data-importo"), 10));
            });
            contenitore.appendChild(btn);
        }

        el("btn-offri").addEventListener("click", function () {
            var input = el("importo-libero");
            var val = input.value.trim();
            var n = val === "" ? null : Number(val);
            if (n !== null && isNaN(n)) n = null;
            inviaOfferta(idLotto, n);
        });
    }

    // --- Aggiornamenti in posto (nessun elemento ricreato) ---

    function aggiornaInfoCalciatore(lotto) {
        var c = trovaCalciatore(lotto.idCalciatore);
        el("lotto-nome").textContent = c ? c.nome : "Calciatore #" + lotto.idCalciatore;
        var ruoloEl = el("lotto-ruolo");
        ruoloEl.textContent = c ? c.ruolo : "";
        ruoloEl.className = "ruolo" + (c ? " ruolo-" + c.ruolo : "");
        el("lotto-dettagli").textContent = c
            ? c.squadra + " · Quotazione " + c.quotazione
            : "";
    }

    function aggiornaOfferta(lotto) {
        var base = lotto.offertaCorrente != null ? lotto.offertaCorrente : 0;

        var area = el("area-offerta");
        if (lotto.offertaCorrente != null) {
            var off = trovaPartecipante(lotto.offerenteCorrente);
            var nomeOff = off ? off.nome : lotto.offerenteCorrente;
            var mia = lotto.offerenteCorrente === codice;
            area.innerHTML =
                '<div class="offerta-importo">' + lotto.offertaCorrente + '</div>' +
                '<div class="offerta-di' + (mia ? ' mia' : '') + '">' +
                (mia ? "Sei in testa" : "di " + esc(nomeOff)) + '</div>';
        } else {
            area.innerHTML = '<div class="offerta-nessuna">Nessuna offerta — base ' + base + '</div>';
        }

        // Ricalcolo dell'importo assoluto sui pulsanti rapidi: si aggiorna l'attributo
        // e l'etichetta, senza sostituire gli elementi.
        var btns = el("buzzer-rapidi").querySelectorAll(".btn-rapido");
        for (var i = 0; i < btns.length; i++) {
            var incremento = parseInt(btns[i].getAttribute("data-incremento"), 10);
            var assoluto = base + incremento;
            btns[i].setAttribute("data-importo", assoluto);
            btns[i].querySelector(".assoluto").textContent = assoluto;
        }
    }

    // Rosa del partecipante, raggruppata per ruolo. I crediti residui arrivano gia'
    // proiettati dal server (totali meno la somma dei prezzi in rosa): qui si mostrano
    // e basta, senza regole ne' capienze. Ogni reparto si riscrive solo se il suo
    // contenuto e' cambiato: durante il countdown la rosa non viene toccata.
    function vociRuolo(partecipante, ruolo) {
        return (partecipante.rosa && partecipante.rosa[ruolo]) || [];
    }

    function aggiornaRosa(partecipante) {
        var quanti = 0;
        var spesa = 0;

        for (var i = 0; i < RUOLI.length; i++) {
            var ruolo = RUOLI[i];
            var voci = vociRuolo(partecipante, ruolo);
            quanti += voci.length;

            // Il listone entra nella firma: quando arriva, i nomi sostituiscono gli id.
            var firma = listone ? "L" : "-";
            for (var j = 0; j < voci.length; j++) {
                spesa += voci[j].prezzo;
                firma += "|" + voci[j].idCalciatore + ":" + voci[j].prezzo;
            }

            if (firmeRosa[ruolo] === firma) continue;
            firmeRosa[ruolo] = firma;

            el("rosa-conteggio-" + ruolo).textContent = voci.length;

            var html = "";
            for (var k = 0; k < voci.length; k++) {
                var c = trovaCalciatore(voci[k].idCalciatore);
                var nome = c ? c.nome : "#" + voci[k].idCalciatore;
                html += '<span class="chip">' + esc(nome) +
                        ' <em>' + voci[k].prezzo + '</em></span>';
            }
            el("rosa-calciatori-" + ruolo).innerHTML =
                html || '<span class="reparto-vuoto">nessuno</span>';
        }

        el("rosa-spesa").textContent = quanti === 0
            ? "nessun calciatore"
            : quanti + (quanti === 1 ? " calciatore · " : " calciatori · ") + spesa + " spesi";
    }

    // --- Ricerca sul listone (tutta locale, nessuna chiamata al server) ---

    // Chi ha in rosa ciascun calciatore, ricavato dalle rose dello snapshot: serve a
    // dire non solo che un calciatore e' preso, ma da chi.
    function proprietariPerCalciatore() {
        var proprietari = {};
        if (!snapshot) return proprietari;
        for (var i = 0; i < snapshot.partecipanti.length; i++) {
            var p = snapshot.partecipanti[i];
            for (var j = 0; j < RUOLI.length; j++) {
                var voci = vociRuolo(p, RUOLI[j]);
                for (var k = 0; k < voci.length; k++) {
                    proprietari[voci[k].idCalciatore] = p.nome;
                }
            }
        }
        return proprietari;
    }

    function insiemeAssegnati() {
        var assegnati = {};
        if (!snapshot || !snapshot.calciatoriAssegnati) return assegnati;
        for (var i = 0; i < snapshot.calciatoriAssegnati.length; i++) {
            assegnati[snapshot.calciatoriAssegnati[i]] = true;
        }
        return assegnati;
    }

    function corrisponde(c) {
        if (filtroRuolo !== "" && c.ruolo !== filtroRuolo) return false;
        var q = filtroNome.trim().toLowerCase();
        return q === "" || c.nome.toLowerCase().indexOf(q) !== -1;
    }

    function rigaRicerca(c, marchio) {
        return '<div class="ris' + (marchio ? " non-disponibile" : "") + '">' +
            '<div class="ris-testa">' +
            '<span class="ris-nome">' + esc(c.nome) + '</span>' +
            '<span class="ruolo ruolo-' + esc(c.ruolo) + '">' + esc(c.ruolo) + '</span>' +
            '<span class="ris-quot">Q ' + c.quotazione + '</span>' +
            '</div>' +
            '<div class="ris-sotto">' + esc(c.squadra) +
            (marchio ? ' <span class="marchio">' + esc(marchio) + '</span>' : "") +
            '</div>' +
            '</div>';
    }

    // Ricostruisce il solo contenuto di #ricerca-risultati, e solo se e' cambiato:
    // il campo nome e i pulsanti ruolo non vengono mai toccati, quindi la ricerca
    // aperta non si resetta ne' perde il focus quando arriva uno snapshot.
    function aggiornaRicerca() {
        var area = el("ricerca-risultati");
        if (!area) return;

        var html;
        if (!listone) {
            html = '<div class="ricerca-nota">Listone in caricamento…</div>';
        } else if (filtroNome.trim() === "" && filtroRuolo === "") {
            html = '<div class="ricerca-nota">Scrivi un nome o scegli un ruolo per cercare.</div>';
        } else {
            var assegnati = insiemeAssegnati();
            var proprietari = proprietariPerCalciatore();

            var liberi = "";
            var quantiLiberi = 0;
            var nonDisponibili = "";
            var quantiNonDisponibili = 0;

            for (var i = 0; i < listone.length; i++) {
                var c = listone[i];
                if (!corrisponde(c)) continue;

                if (assegnati[c.id]) {
                    quantiNonDisponibili++;
                    if (quantiNonDisponibili <= MAX_NON_DISPONIBILI) {
                        var chi = proprietari[c.id];
                        nonDisponibili += rigaRicerca(c, chi ? "preso da " + chi : "gia' preso");
                    }
                } else if (c.fuoriLista) {
                    quantiNonDisponibili++;
                    if (quantiNonDisponibili <= MAX_NON_DISPONIBILI) {
                        nonDisponibili += rigaRicerca(c, "fuori lista");
                    }
                } else {
                    quantiLiberi++;
                    if (quantiLiberi <= MAX_LIBERI) liberi += rigaRicerca(c, null);
                }
            }

            html = '<div class="ricerca-gruppo">Liberi</div>';
            html += quantiLiberi === 0
                ? '<div class="ricerca-nota">Nessun calciatore libero per questa ricerca.</div>'
                : liberi;
            if (quantiLiberi > MAX_LIBERI) {
                html += '<div class="ricerca-nota">Mostrati i primi ' + MAX_LIBERI +
                    ' di ' + quantiLiberi + ': affina la ricerca.</div>';
            }

            if (quantiNonDisponibili > 0) {
                html += '<div class="ricerca-gruppo">Non disponibili</div>' + nonDisponibili;
                if (quantiNonDisponibili > MAX_NON_DISPONIBILI) {
                    html += '<div class="ricerca-nota">Mostrati i primi ' + MAX_NON_DISPONIBILI +
                        ' di ' + quantiNonDisponibili + '.</div>';
                }
            }
        }

        if (firmaRicerca === html) return;
        firmaRicerca = html;
        area.innerHTML = html;
    }

    function mostraMessaggio(tipo, testo) {
        var area = el("area-messaggio");
        if (!area) return;
        area.className = "messaggio-area messaggio " + tipo;
        area.textContent = testo;
    }

    // --- Render principale: decide la vista e aggiorna in posto ---

    function render() {
        if (!snapshot) {
            mostraAttesa();
            return;
        }

        var partecipante = trovaPartecipante(codice);
        if (!partecipante) {
            mostraCodiceAssente();
            return;
        }

        if (vista !== "asta" || !el("p-nome")) {
            costruisciLayoutAsta();
        }

        el("p-nome").textContent = partecipante.nome;
        el("p-crediti").textContent = "Crediti residui: " + partecipante.crediti
                + " di " + partecipante.creditiTotali;
        aggiornaRosa(partecipante);
        aggiornaRicerca();

        var lotto = snapshot.lotto;
        if (lotto) {
            // Lo skeleton resta lo stesso per tutto il ciclo di vita del lotto, anche
            // attraverso pausa e scadenza: cosi' l'input importo non viene ricreato.
            if (statoAreaLotto !== lotto.idLotto) {
                costruisciLottoSkeleton(lotto.idLotto);
                statoAreaLotto = lotto.idLotto;
            }
            aggiornaInfoCalciatore(lotto);
            aggiornaOfferta(lotto);
            aggiornaStato(lotto);
        } else if (statoAreaLotto !== "vuoto") {
            el("area-lotto").innerHTML = '<div class="stato-attesa">Nessun lotto in corso</div>';
            statoAreaLotto = "vuoto";
            statoLottoCorrente = null;
        }
    }

    // Riallinea il countdown al tempo del server e adegua stato dei buzzer e messaggi.
    function aggiornaStato(lotto) {
        statoLottoCorrente = lotto.stato;
        istanteScadenzaLocale = Date.now() + lotto.secondiResidui * 1000;
        scriviCountdown(lotto);

        abilitaBuzzer(lotto.stato === "APERTO");

        var nota = el("area-stato");
        if (!nota) return;
        if (lotto.stato === "SCADUTO") {
            nota.className = "area-stato scaduto";
            if (lotto.offertaCorrente != null) {
                var off = trovaPartecipante(lotto.offerenteCorrente);
                var nomeOff = off ? off.nome : lotto.offerenteCorrente;
                var mia = lotto.offerenteCorrente === codice;
                nota.textContent = "Tempo scaduto — " +
                    (mia ? "sei in testa con " + lotto.offertaCorrente
                         : "offerta vincente " + lotto.offertaCorrente + " di " + nomeOff) +
                    ". In attesa del banditore.";
            } else {
                nota.textContent = "Tempo scaduto senza offerte. In attesa del banditore.";
            }
        } else if (lotto.stato === "AGGIUDICATO") {
            nota.className = "area-stato aggiudicato";
            if (lotto.offerenteCorrente === codice) {
                nota.textContent = "Aggiudicato a te per " + lotto.offertaCorrente + ".";
            } else {
                var offA = trovaPartecipante(lotto.offerenteCorrente);
                var nomeA = offA ? offA.nome : lotto.offerenteCorrente;
                nota.textContent = "Aggiudicato a " + nomeA + " per " + lotto.offertaCorrente + ".";
            }
        } else if (lotto.stato === "IN_PAUSA") {
            nota.className = "area-stato pausa";
            nota.textContent = "Asta in pausa. Le offerte riprenderanno a breve.";
        } else {
            nota.className = "area-stato";
            nota.textContent = "";
        }
    }

    // Scrive il solo nodo #countdown: nessun altro elemento viene toccato.
    function scriviCountdown(lotto) {
        var elc = el("countdown");
        if (!elc) return;
        if (lotto.stato === "SCADUTO") {
            elc.className = "countdown scaduto";
            elc.textContent = "Scaduto";
            return;
        }
        if (lotto.stato === "AGGIUDICATO") {
            elc.className = "countdown aggiudicato";
            elc.textContent = "Aggiudicato";
            return;
        }
        if (lotto.stato === "IN_PAUSA") {
            elc.className = "countdown pausa";
            elc.textContent = lotto.secondiResidui + "s (in pausa)";
            return;
        }
        var secondi = Math.max(0, Math.ceil((istanteScadenzaLocale - Date.now()) / 1000));
        elc.className = "countdown";
        elc.textContent = secondi + "s";
    }

    function abilitaBuzzer(aperto) {
        var contenitore = el("buzzer-rapidi");
        if (contenitore) {
            var btns = contenitore.querySelectorAll(".btn-rapido");
            for (var i = 0; i < btns.length; i++) btns[i].disabled = !aperto;
        }
        var input = el("importo-libero");
        if (input) input.disabled = !aperto;
        var btnOffri = el("btn-offri");
        if (btnOffri) btnOffri.disabled = !aperto;
    }

    // Un solo timer per l'intera pagina: aggiorna il countdown a lotto aperto,
    // riallineandosi a ogni snapshot. In pausa o scaduto il countdown resta fermo.
    function tickCountdown() {
        if (!snapshot || !snapshot.lotto) return;
        if (statoAreaLotto !== snapshot.lotto.idLotto) return;
        if (statoLottoCorrente !== "APERTO") return;
        scriviCountdown(snapshot.lotto);
    }

    function inviaOfferta(idLotto, importo) {
        var xhr = new XMLHttpRequest();
        xhr.open("POST", "/api/offerta");
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.onload = function () {
            if (xhr.status === 200) {
                mostraMessaggio("ok", "Offerta di " + importo + " inviata");
                var input = el("importo-libero");
                if (input) input.value = "";
            } else {
                var motivo = "Offerta rifiutata";
                try {
                    var body = JSON.parse(xhr.responseText);
                    if (body && body.motivo) motivo = body.motivo;
                } catch (e) { /* corpo non JSON: si tiene il messaggio generico */ }
                mostraMessaggio("errore", motivo);
            }
        };
        xhr.onerror = function () {
            mostraMessaggio("errore", "Invio offerta fallito: rete non raggiungibile");
        };
        xhr.send(JSON.stringify({
            idLotto: idLotto,
            codicePartecipante: codice,
            importo: importo
        }));
    }

    function caricaListone() {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "/api/listone");
        xhr.onload = function () {
            if (xhr.status === 200) {
                listone = JSON.parse(xhr.responseText);
                render();
            }
        };
        xhr.send();
    }

    var sse = new EventSource("/api/sse");

    sse.addEventListener("snapshot", function (e) {
        snapshot = JSON.parse(e.data);
        if (!listone) {
            caricaListone();
        }
        render();
    });

    sse.addEventListener("attesa", function () {
        snapshot = null;
        render();
    });

    sse.onerror = function () {
        // EventSource riconnette automaticamente
    };

    setInterval(tickCountdown, CADENZA_COUNTDOWN);

    render();
})();
