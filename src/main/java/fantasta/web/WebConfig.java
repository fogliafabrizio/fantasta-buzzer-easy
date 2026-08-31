package fantasta.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mappa gli URL di directory delle pagine statiche sui rispettivi index.html.
 * Spring Boot serve automaticamente index.html solo per la radice "/", non per
 * le sottocartelle: senza queste rotte "/console/" e "/telefono/" darebbero 404
 * e il QR code (che punta a /telefono/?codice=XXXX) non aprirebbe la pagina.
 *
 * Si usa un forward interno: il browser resta su "/telefono/?codice=XXXX", quindi
 * la query string arriva al client (app.js legge il codice da window.location.search)
 * e gli asset relativi (app.js, style.css) si risolvono sotto la cartella corretta.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/console/").setViewName("forward:/console/index.html");
        registry.addViewController("/telefono/").setViewName("forward:/telefono/index.html");
    }
}
