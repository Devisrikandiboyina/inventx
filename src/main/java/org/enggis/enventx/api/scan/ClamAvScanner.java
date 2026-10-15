package org.enggis.enventx.api.scan;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Streams the file to clamd with the INSTREAM command; nothing is written to disk. */
public class ClamAvScanner implements VirusScanner {

    private static final int CHUNK = 8192;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamAvScanner(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public void scan(InputStream data) {
        String reply;
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            var out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            var buffer = new byte[CHUNK];
            int read;
            while ((read = data.read(buffer)) != -1) {
                out.writeInt(read);
                out.write(buffer, 0, read);
            }
            out.writeInt(0);
            out.flush();
            reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).replace("\0", "").strip();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SCANNER_UNAVAILABLE");
        }
        if (reply.endsWith("FOUND")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_INFECTED");
        }
        if (!reply.endsWith("OK")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SCANNER_UNAVAILABLE");
        }
    }
}