/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ridwanharts.scalemonitor.service;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author ridwan
 */
public class SerialService {

    private SerialPort activePort;
    private Thread readerThread;

    public List<String> listPorts() {
        List<String> list = new ArrayList<>();
        for (SerialPort p : SerialPort.getCommPorts()) {
            list.add(p.getSystemPortName());
        }
        return list;
    }

    public void open(String portName, int baudRate, Consumer<String> onData, Consumer<String> onError) {
        close(); // close existing

        SerialPort port = SerialPort.getCommPort(portName);
        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 0);

        if (!port.openPort()) {
            onError.accept("Unable to open port " + portName);
            return;
        }

        activePort = port;

        readerThread = new Thread(() -> {
            try (InputStream in = port.getInputStream()) {
                StringBuilder sb = new StringBuilder();
                int b;
                while (port.isOpen() && (b = in.read()) != -1) {
                    char ch = (char) b;
                    // build line until newline
                    if (ch == '\n') {
                        String line = sb.toString().replace("\r", "");
                        sb.setLength(0);
                        onData.accept(line);
                    } else {
                        sb.append(ch);
                    }
                }
            } catch (IOException ex) {
                onError.accept(ex.getMessage());
            } finally {
                close();
            }
        }, "Serial-Reader-" + portName);

        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void close() {
        try {
            if (activePort != null && activePort.isOpen()) {
                activePort.closePort();
            }
        } catch (Exception ignored) {}
        activePort = null;

        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        readerThread = null;
    }

    public boolean send(String text) {
        if (activePort == null || !activePort.isOpen()) return false;
        try {
            activePort.getOutputStream().write(text.getBytes());
            activePort.getOutputStream().flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
