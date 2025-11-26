/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ridwanharts.scalemonitor.util;

/**
 * Parses the typical Sayaki A12E ASCII payload.
 * Example payloads:
 *  - wn000125.4kg
 *  - wn000000.0kg
 *
 * Returns normalized decimal like "125.4" or "0.0"
 */
public class WeightParser {

    public String parseWeight(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;

        // Some devices prefix with non-printable — remove leading control chars
        // Keep only printable ascii
        raw = raw.replaceAll("[^\\x20-\\x7E]", "").trim();

        // Typical prefix: "wn"
        if (raw.startsWith("wn")) {
            raw = raw.substring(2);
        } else if (raw.startsWith("w")) { // just in case
            raw = raw.substring(1);
        }

        // remove suffix unit like "kg"
        if (raw.endsWith("kg")) {
            raw = raw.substring(0, raw.length() - 2);
        }

        // Remove any spaces
        raw = raw.trim();

        // Some devices pad with leading zeros. Convert to numeric and format simply.
        try {
            double val = Double.parseDouble(raw);
            // Format: remove trailing .0 if not needed? We'll keep one decimal to match device.
            return String.format("%.1f", val);
        } catch (NumberFormatException e) {
            // If parsing fails, return original trimmed string to help debugging
            return raw;
        }
    }
}
