(function () {
    "use strict";

    var app = document.getElementById("app");
    var codice = new URLSearchParams(window.location.search).get("codice");

    if (!codice) {
        app.innerHTML = '<div class="errore">Codice partecipante mancante nell\'URL</div>';
        return;
    }

    var listone = null;
    var snapshot = null;
    var nomePartecipante = null;

    function render() {
        if (!snapshot) {
            app.innerHTML =
                '<div class="intestazione"><h1>Fantasta</h1></div>' +
                '<div class="stato-attesa">Connessione in corso…</div>';
            return;
        }

        var partecipante = null;
        for (var i = 0; i < snapshot.partecipanti.length; i++) {
            if (snapshot.partecipanti[i].codice === codice) {
                partecipante = snapshot.partecipanti[i];
                break;
            }
        }

        if (!partecipante) {
            app.innerHTML = '<div class="errore">Codice ' + codice + ' non trovato nell\'asta</div>';
            return;
        }

        nomePartecipante = partecipante.nome;

        var html =
            '<div class="intestazione">' +
            '<h1>' + esc(partecipante.nome) + '</h1>' +
            '<div class="codice">Codice: ' + esc(codice) + '</div>' +
            '</div>' +
            '<div class="stato-attesa">Nessun lotto in corso</div>';

        app.innerHTML = html;
    }

    function esc(s) {
        var d = document.createElement("div");
        d.textContent = s;
        return d.innerHTML;
    }

    function caricaListone() {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "/api/listone");
        xhr.onload = function () {
            if (xhr.status === 200) {
                listone = JSON.parse(xhr.responseText);
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
