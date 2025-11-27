/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ridwanharts.scalemonitor.service;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.ridwanharts.scalemonitor.util.ScaleFrameParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Serial communication service with multiple parsing modes for testing device protocols.
 * @author ridwan
 */
public class SerialService {

    /**
     * Enum to select data parsing mode for debugging different device protocols
     */
    public enum ParseMode {
        LINE_BASED,        // Mode 1: Read and accept when contain \n, else append to StringBuilder
        FRAME_PARSER,      // Mode 2: Use ScaleFrameParser for fixed 15-byte frames
        RAW_DEBUG          // Mode 3: Raw hex dump for debugging unknown protocols
    }

    private SerialPort activePort;
    private SerialPortDataListener dataListener;
    private final StringBuilder sb = new StringBuilder();
    private ScaleFrameParser parser;
    private ParseMode parseMode = ParseMode.RAW_DEBUG; // Default to RAW_DEBUG to see what device sends

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

        // Use event-driven reads (no read timeout dependency)
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) {
            onError.accept("Unable to open port " + portName);
            return;
        }

        activePort = port;
        parser = new ScaleFrameParser(); // new parser instance for this session

        dataListener = new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
                try {
                    int available = port.bytesAvailable();
                    if (available <= 0) return;
                    byte[] buffer = new byte[available];
                    int read = port.readBytes(buffer, buffer.length);
                    if (read > 0) {
                        switch (parseMode) {
                            case LINE_BASED:
                                parseLineBasedData(buffer, read, onData);
                                break;
                            case FRAME_PARSER:
                                parseFrameData(buffer, read, onData, onError);
                                break;
                            case RAW_DEBUG:
                                parseRawDebugData(buffer, read, onData);
                                break;
                        }
                    }
                } catch (Exception ex) {
                    onError.accept(ex.getMessage());
                }
            }
        };

        port.addDataListener(dataListener);
    }

    /**
     * Mode 1: LINE_BASED - Buffers data until newline character is found
     */
    private void parseLineBasedData(byte[] buffer, int read, Consumer<String> onData) {
        String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
        synchronized (sb) {
            for (char ch : chunk.toCharArray()) {
                if (ch == '\n') {
                    String line = sb.toString().replace("\r", "");
                    sb.setLength(0);
                    onData.accept(line);
                } else {
                    sb.append(ch);
                }
            }
        }
    }

    /**
     * Mode 2: FRAME_PARSER - Uses ScaleFrameParser for fixed 15-byte frame protocol
     */
    private void parseFrameData(byte[] buffer, int read, Consumer<String> onData, Consumer<String> onError) {
        byte[] chunk = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
        parser.feed(chunk, onData, onError);
    }

    /**
     * Mode 3: RAW_DEBUG - Converts raw bytes to hex string for debugging unknown protocols
     */
    private void parseRawDebugData(byte[] buffer, int read, Consumer<String> onData) {
        byte[] chunk = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
        StringBuilder hex = new StringBuilder();
        for (byte b : chunk) {
            hex.append(String.format("%02X ", b));
        }
        onData.accept("[RAW HEX] " + hex);
    }

    /**
     * Switch the parsing mode - useful for testing which mode matches your device protocol
     * @param mode The ParseMode to use
     */
    public void setParseMode(ParseMode mode) {
        this.parseMode = mode;
        // Clear buffer when switching modes
        synchronized (sb) {
            sb.setLength(0);
        }
    }

    /**
     * Get current parsing mode
     */
    public ParseMode getParseMode() {
        return parseMode;
    }

    public void close() {
        try {
            if (activePort != null) {
                if (dataListener != null) {
                    activePort.removeDataListener();
                    dataListener = null;
                }
                if (activePort.isOpen()) {
                    activePort.closePort();
                }
            }
        } catch (Exception ignored) {}
        activePort = null;
        parser = null;
        synchronized (sb) {
            sb.setLength(0);
        }
    }

    public boolean send(String text) {
        if (activePort == null || !activePort.isOpen()) return false;
        try {
            activePort.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
            activePort.getOutputStream().flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
