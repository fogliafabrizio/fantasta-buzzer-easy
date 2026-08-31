package fantasta.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import fantasta.asta.Asta;
import fantasta.asta.LottoScadutoEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    private static final long TIMEOUT_SSE = 0L;

    private final Asta asta;
    private final ObjectMapper mapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor;

    public SseController(Asta asta, ObjectMapper mapper) {
        this.asta = asta;
        this.mapper = mapper;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeatExecutor.scheduleAtFixedRate(this::inviaHeartbeat, 15, 15, TimeUnit.SECONDS);
    }

    @GetMapping(path = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connetti() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_SSE);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("Connessione SSE chiusa, client rimanenti: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("Connessione SSE scaduta, client rimanenti: {}", emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.info("Errore connessione SSE: {}, client rimanenti: {}", e.getMessage(), emitters.size());
        });

        emitters.add(emitter);

        try {
            if (asta.isAttiva()) {
                Snapshot snapshot = asta.generaSnapshot();
                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(mapper.writeValueAsString(snapshot), MediaType.APPLICATION_JSON));
            } else {
                emitter.send(SseEmitter.event()
                        .name("attesa")
                        .data(""));
            }
        } catch (IOException e) {
            emitters.remove(emitter);
            log.warn("Invio snapshot iniziale fallito: {}", e.getMessage());
        }

        log.info("Nuova connessione SSE, client totali: {}", emitters.size());
        return emitter;
    }

    /**
     * Alla scadenza il countdown scatta lato server, fuori da una richiesta HTTP:
     * l'Asta pubblica un evento applicativo e qui si fa ripartire il broadcast dello
     * snapshot, cosi' console e telefoni vedono subito lo stato SCADUTO.
     */
    @EventListener
    public void onLottoScaduto(LottoScadutoEvent evento) {
        log.info("Lotto {} scaduto: broadcast dello snapshot ai client", evento.getIdLotto());
        broadcast();
    }

    public void broadcast() {
        if (!asta.isAttiva()) return;

        Snapshot snapshot = asta.generaSnapshot();
        String json;
        try {
            json = mapper.writeValueAsString(snapshot);
        } catch (IOException e) {
            log.error("Serializzazione snapshot fallita: {}", e.getMessage());
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public void broadcastAttesa() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("attesa")
                        .data(""));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    private void inviaHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(""));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
    }
}
