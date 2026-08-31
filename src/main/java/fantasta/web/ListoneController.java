package fantasta.web;

import fantasta.asta.Asta;
import fantasta.listone.Calciatore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ListoneController {

    private final Asta asta;

    public ListoneController(Asta asta) {
        this.asta = asta;
    }

    @GetMapping("/api/listone")
    public ResponseEntity<List<Calciatore>> listone() {
        if (!asta.isAttiva()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(asta.getCalciatori());
    }
}
