package com.kyv3.addon.modules.flow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;
import com.kyv3.addon.modules.flow.v2.domain.FlowGraph;
import com.kyv3.addon.modules.flow.v2.domain.FlowLink;
import com.kyv3.addon.modules.flow.v2.domain.FlowNode;
import com.kyv3.addon.modules.flow.v2.domain.FlowNodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowPresetCodecTest {
    private static final Gson GSON = new Gson();

    @Test
    void signedPresetRoundTripIsVerified() {
        FlowDefinition definition = sampleDefinition();

        FlowPresetCodec.ExportResult exported = FlowPresetCodec.export(definition, "secret-key");
        assertTrue(exported.success());
        assertTrue(exported.signed());

        FlowPresetCodec.ImportResult imported = FlowPresetCodec.importPreset(exported.payload(), "secret-key");
        assertTrue(imported.success());
        assertTrue(imported.checksumValid());
        assertTrue(imported.signed());
        assertTrue(imported.signatureVerified());
        assertNotNull(imported.definition());
    }

    @Test
    void tamperedChecksumIsRejected() {
        FlowPresetCodec.ExportResult exported = FlowPresetCodec.export(sampleDefinition(), "secret-key");
        assertTrue(exported.success());

        JsonObject root = GSON.fromJson(exported.payload(), JsonObject.class);
        root.addProperty("checksum", "00badchecksum");

        FlowPresetCodec.ImportResult imported = FlowPresetCodec.importPreset(GSON.toJson(root), "secret-key");
        assertFalse(imported.success());
        assertFalse(imported.checksumValid());
    }

    @Test
    void legacyUnsignedPresetIsStillLoadable() {
        FlowDefinition definition = sampleDefinition();
        String legacyPayload = GSON.toJson(definition);

        FlowPresetCodec.ImportResult imported = FlowPresetCodec.importPreset(legacyPayload, "secret-key");
        assertTrue(imported.success());
        assertTrue(imported.checksumValid());
        assertFalse(imported.signed());
    }

    private static FlowDefinition sampleDefinition() {
        FlowGraph graph = new FlowGraph(
            3,
            List.of(
                new FlowNode(1, FlowNodeKind.OnEnable, 0, 0, "", 1),
                new FlowNode(2, FlowNodeKind.SendCommand, 120, 0, "spawn", 1)
            ),
            List.of(new FlowLink(1, 2))
        );

        return new FlowDefinition(10, "preset-sample", true, false, graph);
    }
}
