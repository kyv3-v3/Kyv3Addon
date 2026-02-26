package com.kyv3.addon.modules.flow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class FlowPresetCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int CURRENT_PRESET_VERSION = 1;

    private FlowPresetCodec() {
    }

    public static ExportResult export(FlowDefinition definition, String signingKey) {
        if (definition == null) {
            return ExportResult.failure("Definition is null.");
        }

        FlowDefinition payloadDefinition = definition.copy();
        payloadDefinition.setId(0);
        payloadDefinition.setActive(false);
        payloadDefinition.sanitize();

        String payloadJson = GSON.toJson(payloadDefinition);
        String checksum = sha256Hex(payloadJson);
        String signature = sign(checksum, signingKey);

        Envelope envelope = new Envelope();
        envelope.presetVersion = CURRENT_PRESET_VERSION;
        envelope.exportedAt = System.currentTimeMillis();
        envelope.moduleName = payloadDefinition.name();
        envelope.payloadJson = payloadJson;
        envelope.checksum = checksum;
        envelope.signature = signature;

        return ExportResult.success(GSON.toJson(envelope), checksum, !signature.isBlank());
    }

    public static ImportResult importPreset(String rawPreset, String signingKey) {
        if (rawPreset == null || rawPreset.isBlank()) {
            return ImportResult.failure("Preset payload is empty.");
        }

        try {
            Envelope envelope = GSON.fromJson(rawPreset, Envelope.class);
            if (envelope != null && envelope.payloadJson != null && envelope.checksum != null) {
                String calculatedChecksum = sha256Hex(envelope.payloadJson);
                boolean checksumValid = calculatedChecksum.equalsIgnoreCase(envelope.checksum);

                FlowDefinition definition = GSON.fromJson(envelope.payloadJson, FlowDefinition.class);
                if (definition == null) {
                    return ImportResult.failure("Preset payload is invalid.");
                }
                definition.sanitize();

                boolean signed = envelope.signature != null && !envelope.signature.isBlank();
                boolean signatureVerified = signed && verifySignature(envelope.checksum, envelope.signature, signingKey);

                if (!checksumValid) {
                    return ImportResult.withStatus(
                        false,
                        definition,
                        false,
                        signed,
                        signatureVerified,
                        "Checksum mismatch. Preset may be corrupted or tampered."
                    );
                }

                return ImportResult.withStatus(
                    true,
                    definition,
                    true,
                    signed,
                    signatureVerified,
                    signed ? (signatureVerified ? "Signed preset verified." : "Signature present but not verified.") : "Unsigned preset loaded."
                );
            }
        } catch (Exception ignored) {
        }

        try {
            FlowDefinition legacy = GSON.fromJson(rawPreset, FlowDefinition.class);
            if (legacy == null) {
                return ImportResult.failure("Unable to parse preset payload.");
            }

            legacy.sanitize();
            return ImportResult.withStatus(true, legacy, true, false, false, "Legacy unsigned preset loaded.");
        } catch (Exception ignored) {
            return ImportResult.failure("Unable to parse preset payload.");
        }
    }

    private static boolean verifySignature(String checksum, String signature, String signingKey) {
        if (signature == null || signature.isBlank()) return false;
        String expected = sign(checksum, signingKey);
        return !expected.isBlank() && expected.equalsIgnoreCase(signature);
    }

    private static String sign(String checksum, String signingKey) {
        if (checksum == null || checksum.isBlank()) return "";
        if (signingKey == null || signingKey.isBlank()) return "";

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(checksum.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static final class ExportResult {
        private final boolean success;
        private final String payload;
        private final String checksum;
        private final boolean signed;
        private final String message;

        private ExportResult(boolean success, String payload, String checksum, boolean signed, String message) {
            this.success = success;
            this.payload = payload == null ? "" : payload;
            this.checksum = checksum == null ? "" : checksum;
            this.signed = signed;
            this.message = message == null ? "" : message;
        }

        public static ExportResult success(String payload, String checksum, boolean signed) {
            return new ExportResult(true, payload, checksum, signed, "");
        }

        public static ExportResult failure(String message) {
            return new ExportResult(false, "", "", false, message);
        }

        public boolean success() {
            return success;
        }

        public String payload() {
            return payload;
        }

        public String checksum() {
            return checksum;
        }

        public boolean signed() {
            return signed;
        }

        public String message() {
            return message;
        }
    }

    public static final class ImportResult {
        private final boolean success;
        private final FlowDefinition definition;
        private final boolean checksumValid;
        private final boolean signed;
        private final boolean signatureVerified;
        private final String message;

        private ImportResult(boolean success, FlowDefinition definition, boolean checksumValid, boolean signed, boolean signatureVerified, String message) {
            this.success = success;
            this.definition = definition == null ? null : definition.copy();
            this.checksumValid = checksumValid;
            this.signed = signed;
            this.signatureVerified = signatureVerified;
            this.message = message == null ? "" : message;
        }

        public static ImportResult withStatus(boolean success, FlowDefinition definition, boolean checksumValid, boolean signed, boolean signatureVerified, String message) {
            return new ImportResult(success, definition, checksumValid, signed, signatureVerified, message);
        }

        public static ImportResult failure(String message) {
            return new ImportResult(false, null, false, false, false, message);
        }

        public boolean success() {
            return success;
        }

        public FlowDefinition definition() {
            return definition == null ? null : definition.copy();
        }

        public boolean checksumValid() {
            return checksumValid;
        }

        public boolean signed() {
            return signed;
        }

        public boolean signatureVerified() {
            return signatureVerified;
        }

        public String message() {
            return message;
        }
    }

    private static final class Envelope {
        private int presetVersion = CURRENT_PRESET_VERSION;
        private long exportedAt;
        private String moduleName;
        private String payloadJson;
        private String checksum;
        private String signature;
    }
}
