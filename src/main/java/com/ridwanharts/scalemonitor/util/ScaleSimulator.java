package com.ridwanharts.scalemonitor.util;

import com.fazecast.jSerialComm.SerialPort;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Random;

public class ScaleSimulator {

    public static void main(String[] args) throws Exception {
        String portName = (args.length > 0) ? args[0] : "COM3";
        int baud = (args.length > 1) ? Integer.parseInt(args[1]) : 9600;

        SerialPort port = SerialPort.getCommPort(portName);
        port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (port.isOpen()) port.closePort();
        if (!port.openPort()) {
            System.err.println("Unable to open " + portName);
            return;
        }
        System.out.println("Scale simulator connected on " + portName);

        SerialPort finalPort = port;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (finalPort.isOpen()) {
                System.out.println("Closing " + portName + "...");
                finalPort.closePort();
            }
        }));

        DecimalFormat df = new DecimalFormat("000.000"); // produces 7 chars like "004.139" or "-04.139"
        Random rnd = new Random();
        int counter = 0;

        try {
            while (true) {
                byte[] frame = new byte[15];

                // Occasionally send overload / low messages
                boolean sendOL = (rnd.nextInt(100) < 5);  // 5% chance
                boolean sendLO = (!sendOL && rnd.nextInt(1000) < 2); // ~0.2% chance

                if (sendOL) {
                    // 1..9 bytes are spaces, 10='O', 11='L'
                    for (int i = 0; i < 9; i++) frame[i] = 0x20;
                    frame[9] = (byte) 'O';
                    frame[10] = (byte) 'L';
                } else if (sendLO) {
                    for (int i = 0; i < 9; i++) frame[i] = 0x20;
                    frame[9] = (byte) 'L';
                    frame[10] = (byte) 'O';
                } else {
                    // Normal weighing: 1='W', 2='G' (gross)
                    frame[0] = (byte) 'W';
                    frame[1] = (byte) 'G';
                    // generate weight - can be negative occasionally
                    double w = 10.0 + rnd.nextDouble() * 40.0;
                    if (rnd.nextInt(1000) < 5) w = -w; // rare negative
                    String weightStr = df.format(w); // 7 chars
                    byte[] ws = weightStr.getBytes(StandardCharsets.US_ASCII);
                    System.arraycopy(ws, 0, frame, 2, 7); // bytes 3..9
                    frame[9] = (byte) 'k';
                    frame[10] = (byte) 'g';
                }

                // Compute checksum over bytes 0..10
                int sum = 0;
                for (int i = 0; i <= 10; i++) {
                    sum += Byte.toUnsignedInt(frame[i]);
                }
                sum &= 0xFF;
                int high = (sum >> 4) & 0x0F;
                int low = sum & 0x0F;
                int expected12 = (high <= 9) ? (high + 0x30) : (high + 0x37);
                int expected13 = (low  <= 9) ? (low  + 0x30) : (low  + 0x37);
                frame[11] = (byte) expected12;
                frame[12] = (byte) expected13;
                frame[13] = 0x0D;
                frame[14] = 0x0A;

                // Write frame
                finalPort.writeBytes(frame, frame.length);

                // optional console log every N sends
                if (++counter % 10 == 0) {
                    System.out.println("Sent frame #" + counter);
                }

                Thread.sleep(1000);
            }
        } finally {
            if (port != null && port.isOpen()) port.closePort();
        }
    }
}