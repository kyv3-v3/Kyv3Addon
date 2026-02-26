package com.kyv3.addon.modules.flow.v2.infrastructure.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class FlowRegistryMigrationAssistant {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private FlowRegistryMigrationAssistant() {
    }

    public static JsonObject migrate(JsonObject rawRoot) {
        JsonObject root = rawRoot == null ? new JsonObject() : rawRoot.deepCopy();

        int schemaVersion = intOrDefault(root, "schemaVersion", 1);
        if (schemaVersion < 1) schemaVersion = 1;

        while (schemaVersion < CURRENT_SCHEMA_VERSION) {
            if (schemaVersion == 1) {
                migrateV1ToV2(root);
            }
            schemaVersion++;
        }

        root.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
        return root;
    }

    private static void migrateV1ToV2(JsonObject root) {
        JsonArray definitions = arrayOrCreate(root, "definitions");

        for (int i = 0; i < definitions.size(); i++) {
            JsonElement definitionElement = definitions.get(i);
            if (!(definitionElement instanceof JsonObject definition)) continue;

            JsonObject graph = objectOrCreate(definition, "graph");
            if (!graph.has("nextNodeId") || !graph.get("nextNodeId").isJsonPrimitive()) {
                graph.addProperty("nextNodeId", 1);
            }

            JsonArray nodes = arrayOrCreate(graph, "nodes");
            for (int n = 0; n < nodes.size(); n++) {
                JsonElement nodeElement = nodes.get(n);
                if (!(nodeElement instanceof JsonObject node)) continue;

                if (!node.has("kind") || node.get("kind").isJsonNull()) {
                    node.addProperty("kind", "Notify");
                }

                if (!node.has("text") || node.get("text").isJsonNull()) {
                    node.addProperty("text", "");
                }

                if (!node.has("number") || !node.get("number").isJsonPrimitive()) {
                    node.addProperty("number", 1);
                }
            }

            arrayOrCreate(graph, "links");
        }
    }

    private static int intOrDefault(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static JsonArray arrayOrCreate(JsonObject object, String key) {
        if (object == null) return new JsonArray();

        JsonElement element = object.get(key);
        if (element instanceof JsonArray array) return array;

        JsonArray created = new JsonArray();
        object.add(key, created);
        return created;
    }

    private static JsonObject objectOrCreate(JsonObject object, String key) {
        if (object == null) return new JsonObject();

        JsonElement element = object.get(key);
        if (element instanceof JsonObject nested) return nested;

        JsonObject created = new JsonObject();
        object.add(key, created);
        return created;
    }
}
