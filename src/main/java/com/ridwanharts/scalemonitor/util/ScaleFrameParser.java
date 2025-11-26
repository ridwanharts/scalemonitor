package com.ridwanharts.scalemonitor.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple fixed\-frame parser for the 15\-byte protocol described in the docs.
 * Use feed(...) from your serial data callback and provide onData/onError consumers.
 */
public class ScaleFrameParser {

    private final List<Byte> buf = new ArrayList<>();

    public void feed(byte[] chunk, Consumer<String> onData, Consumer<String> onError) {
        for (byte b : chunk) buf.add(b);

        while (buf.size() >= 15) {
            // Check for CR LF at positions 13 and 14 of the candidate frame
            int b13 = Byte.toUnsignedInt(buf.get(13));
            int b14 = Byte.toUnsignedInt(buf.get(14));
            if (b13 == 0x0D && b14 == 0x0A) {
                byte[] frame = new byte[15];
                for (int i = 0; i < 15; i++) frame[i] = buf.get(i);
                // remove first 15 bytes
                for (int i = 0; i < 15; i++) buf.remove(0);
                processFrame(frame, onData, onError);
            } else {
                // Not aligned: drop one byte and try again
                buf.remove(0);
            }
        }
    }

    private void processFrame(byte[] f, Consumer<String> onData, Consumer<String> onError) {
        try {
            // verify checksum over bytes 0..10
            int sum = 0;
            for (int i = 0; i <= 10; i++) sum += Byte.toUnsignedInt(f[i]);
            sum &= 0xFF;
            int high = (sum >> 4) & 0x0F;
            int low = sum & 0x0F;
            int expected12 = (high <= 9) ? (high + 0x30) : (high + 0x37);
            int expected13 = (low  <= 9) ? (low  + 0x30) : (low  + 0x37);
            if (Byte.toUnsignedInt(f[11]) != expected12 || Byte.toUnsignedInt(f[12]) != expected13) {
                onError.accept("checksum mismatch");
                return;
            }

            // Detect overload / low cases: first 9 bytes are spaces, bytes 9/10 contain OL or LO
            boolean first9Spaces = true;
            for (int i = 0; i < 9; i++) {
                if (Byte.toUnsignedInt(f[i]) != 0x20) { first9Spaces = false; break; }
            }
            char b9 = (char) Byte.toUnsignedInt(f[9]);
            char b10 = (char) Byte.toUnsignedInt(f[10]);

            if (first9Spaces && b9 == 'O' && b10 == 'L') {
                onData.accept("OL"); // overload
                return;
            }
            if (first9Spaces && b9 == 'L' && b10 == 'O') {
                onData.accept("LO"); // low / underflow
                return;
            }

            // Normal weighing frame expected: byte0 == 'W'
            if ((char) Byte.toUnsignedInt(f[0]) == 'W') {
                char mode = (char) Byte.toUnsignedInt(f[1]); // 'G' or 'N'
                String rawWeight = new String(f, 2, 7, StandardCharsets.UTF_8); // 3rd-9th bytes
                String weight = rawWeight.replace(" ", ""); // remove blanks if no decimal
                String unit = new String(f, 9, 2, StandardCharsets.UTF_8).trim(); // 10th-11th bytes
                // emit a compact normalized string: e.g. "G:4.139 kg"
                onData.accept(mode + ":" + weight + " " + unit);
                return;
            }

            // Unknown frame type: return raw hex for debugging
            onError.accept("unknown frame");
        } catch (Exception ex) {
            onError.accept("parser error: " + ex.getMessage());
        }
    }
}
