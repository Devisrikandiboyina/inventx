package org.enggis.enventx.api.scan;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClamAvScannerTest {

    private static ServerSocket fakeClamd(String reply) throws IOException {
        var server = new ServerSocket(0);
        Thread.startVirtualThread(() -> {
            try (var socket = server.accept()) {
                var in = socket.getInputStream();
                // read until the zero-length terminating chunk has been received
                var tail = new byte[4];
                int b;
                while ((b = in.read()) != -1) {
                    System.arraycopy(tail, 1, tail, 0, 3);
                    tail[3] = (byte) b;
                    if (tail[0] == 0 && tail[1] == 0 && tail[2] == 0 && tail[3] == 0 && in.available() == 0) {
                        break;
                    }
                }
                socket.getOutputStream().write(reply.getBytes(StandardCharsets.US_ASCII));
            } catch (IOException ignored) {
            }
        });
        return server;
    }

    private static ByteArrayInputStream data() {
        return new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cleanFilePasses() throws IOException {
        try (var server = fakeClamd("stream: OK\0")) {
            var scanner = new ClamAvScanner("127.0.0.1", server.getLocalPort(), 5000);
            assertThatCode(() -> scanner.scan(data())).doesNotThrowAnyException();
        }
    }

    @Test
    void infectedFileIsRejectedWith422() throws IOException {
        try (var server = fakeClamd("stream: Eicar-Test-Signature FOUND\0")) {
            var scanner = new ClamAvScanner("127.0.0.1", server.getLocalPort(), 5000);
            assertThatThrownBy(() -> scanner.scan(data()))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode().value()).isEqualTo(422))
                    .hasMessageContaining("FILE_INFECTED");
        }
    }

    @Test
    void unreachableScannerIs503() throws IOException {
        int port;
        try (var s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        var scanner = new ClamAvScanner("127.0.0.1", port, 1000);
        assertThatThrownBy(() -> scanner.scan(data()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void errorReplyIs503() throws IOException {
        try (var server = fakeClamd("INSTREAM size limit exceeded. ERROR\0")) {
            var scanner = new ClamAvScanner("127.0.0.1", server.getLocalPort(), 5000);
            assertThatThrownBy(() -> scanner.scan(data()))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode().value()).isEqualTo(503));
        }
    }
}