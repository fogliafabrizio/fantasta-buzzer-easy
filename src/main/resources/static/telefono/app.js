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

    // Layout base dell'asta (intestazione + contenitore del lotto): costruito una sola volta.
    function costruisciLayoutAsta() {
        vista = "asta";
        statoAreaLotto = null;
        app.innerHTML =
            '<div class="intestazione">' +
            '<h1 id="p-nome"></h1>' +
            '<div class="codice" id="p-crediti"></div>' +
            '</div>' +
            '<div id="area-lotto"></div>';
    }

    // Skeleton del lotto: costruito una volta per idLotto. Qui vivono l'input e i
    // pulsanti rapidi, che non vanno ricreati finche' resta lo stesso lotto.
    function costruisciLottoSkeleton(idLotto) {
        el("area-lotto").innerHTML =
            '<div class="lotto">' +
            '<div class="lotto-testa">' +
            '<span class="lotto-nome" id="lotto-nome"></span>' +
            '<span class="ruolo" id="lotto-ruolo"></span>' +
            '</div>' +
            '<div class="lotto-dettagli" id="lotto-dettagli"></div>' +
            '<div class="lotto-offerta" id="area-offerta"></div>' +
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
        el("p-crediti").textContent = "Crediti: " + partecipante.crediti;

        var lotto = snapshot.lotto;
        if (lotto && lotto.stato === "APERTO") {
            if (statoAreaLotto !== lotto.idLotto) {
                costruisciLottoSkeleton(lotto.idLotto);
                statoAreaLotto = lotto.idLotto;
            }
            aggiornaInfoCalciatore(lotto);
            aggiornaOfferta(lotto);
        } else if (statoAreaLotto !== "vuoto") {
            el("area-lotto").innerHTML = '<div class="stato-attesa">Nessun lotto in corso</div>';
            statoAreaLotto = "vuoto";
        }
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

    render();
})();
