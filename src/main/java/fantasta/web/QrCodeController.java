package fantasta.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import fantasta.asta.Asta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@RestController
public class QrCodeController {

    private static final Logger log = LoggerFactory.getLogger(QrCodeController.class);

    private final Asta asta;
    private final int porta;

    public QrCodeController(Asta asta, @Value("${server.port}") int porta) {
        this.asta = asta;
        this.porta = porta;
    }

    @GetMapping(value = "/api/qrcode/{codice}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@PathVariable String codice) {
        if (!asta.isAttiva() || asta.trovaPartecipante(codice) == null) {
            return ResponseEntity.notFound().build();
        }

        String ip = rilevaIp();
        String url = "http://" + ip + ":" + porta + "/telefono/?codice=" + codice;

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return ResponseEntity.ok(out.toByteArray());
        } catch (WriterException | IOException e) {
            log.error("Generazione QR code fallita per codice {}: {}", codice, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/qrcode/{codice}/url")
    public ResponseEntity<String> qrCodeUrl(@PathVariable String codice) {
        if (!asta.isAttiva() || asta.trovaPartecipante(codice) == null) {
            return ResponseEntity.notFound().build();
        }
        String ip = rilevaIp();
        return ResponseEntity.ok("http://" + ip + ":" + porta + "/telefono/?codice=" + codice);
    }

    private String rilevaIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> indirizzi = ni.getInetAddresses();
                while (indirizzi.hasMoreElements()) {
                    InetAddress addr = indirizzi.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Rilevamento IP fallito, uso localhost: {}", e.getMessage());
        }
        return "localhost";
    }
}
