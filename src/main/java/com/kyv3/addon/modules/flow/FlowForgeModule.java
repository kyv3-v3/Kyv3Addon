package com.kyv3.addon.modules.flow;

import com.kyv3.addon.Kyv3Addon;
import com.kyv3.addon.modules.flow.v2.api.FlowProjectKernel;
import com.kyv3.addon.modules.flow.v2.application.FlowRegistryService;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeProfile;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;
import com.kyv3.addon.modules.flow.v2.domain.FlowNodeKind;
import com.kyv3.addon.modules.flow.v2.infrastructure.runtime.MeteorFlowRuntimeGateway;
import com.kyv3.addon.modules.flow.v2.infrastructure.store.JsonFlowRegistryStore;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FlowForgeModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRuntime = settings.createGroup("Runtime");
    private final SettingGroup sgReliability = settings.createGroup("Reliability");
    private final SettingGroup sgSecurity = settings.createGroup("Security");

    private final Setting<Boolean> openEditorOnActivate = sgGeneral.add(new BoolSetting.Builder()
        .name("open-editor-on-activate")
        .description("Opens the Flow Forge editor screen when this module is enabled.")
        .defaultValue(true)
        .build());

    private final Setting<Keybind> openEditorBind = sgGeneral.add(new KeybindSetting.Builder()
        .name("open-editor-bind")
        .description("Opens the editor while this module is active.")
        .defaultValue(Keybind.none())
        .build());

    private final Setting<Boolean> caseInsensitiveChatMatch = sgRuntime.add(new BoolSetting.Builder()
        .name("case-insensitive-chat-match")
        .description("Chat trigger nodes ignore case when matching.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxActionsPerTick = sgRuntime.add(new IntSetting.Builder()
        .name("max-actions-per-tick")
        .description("Runtime safety limit for action nodes per custom module per tick.")
        .defaultValue(24)
        .min(1)
        .sliderMax(256)
        .build());

    private final Setting<Boolean> autoCleanupOnSave = sgReliability.add(new BoolSetting.Builder()
        .name("auto-cleanup-on-save")
        .description("Cleans invalid graph structures every time the graph is saved.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> pruneUnreachableOnCleanup = sgReliability.add(new BoolSetting.Builder()
        .name("cleanup-prune-unreachable")
        .description("When cleanup runs, also removes action nodes unreachable from any event node.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enforceSignedPresets = sgSecurity.add(new BoolSetting.Builder()
        .name("enforce-signed-presets")
        .description("Rejects imported presets unless signature verification succeeds.")
        .defaultValue(false)
        .build());

    private final Setting<String> presetSigningKey = sgSecurity.add(new StringSetting.Builder()
        .name("preset-signing-key")
        .description("Secret key used to sign and verify exported/imported presets.")
        .defaultValue("")
        .build());

    private final Setting<String> registryData = sgGeneral.add(new StringSetting.Builder()
        .name("registry-data")
        .description("Internal Flow Forge registry storage.")
        .defaultValue("")
        .visible(() -> false)
        .build());

    private final FlowProjectKernel v2Kernel = new FlowProjectKernel(
        new JsonFlowRegistryStore(registryData::get, registryData::set),
        new MeteorFlowRuntimeGateway(this, this::maxActionsBudget, this::isCaseInsensitiveChatMatch)
    );

    private final List<CustomFlowDefinition> definitions = new ArrayList<>();

    private int selectedDefinitionId = -1;
    private boolean initialized;
    private boolean bindPressed;

    public FlowForgeModule() {
        super(Kyv3Addon.CUSTOM_CATEGORY, "flow-forge", "Create and edit custom drag-and-connect modules.");
    }

    public FlowProjectKernel architectureKernel() {
        return v2Kernel;
    }

    @Override
    public void onActivate() {
        ensureInitialized();

        if (openEditorOnActivate.get()) {
            openEditor();
        }
    }

    @Override
    public void onDeactivate() {
        bindPressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean pressed = openEditorBind.get().isPressed();
        if (pressed && !bindPressed) {
            ensureInitialized();
            openEditor();
        }
        bindPressed = pressed;
    }

    public void ensureInitialized() {
        ensureHiddenFromClickGui();
        if (initialized) return;

        FlowRegistryService service = v2Kernel.registryService();
        service.ensureInitialized();

        reloadCacheFromService();
        initialized = true;
    }

    public void onRuntimeDefinitionStateChanged() {
        v2Kernel.registryService().syncRuntimeStateToRegistry();
        reloadCacheFromService();
        initialized = true;
    }

    public void openEditor() {
        ensureInitialized();
        if (mc == null) return;
        mc.execute(() -> mc.setScreen(new FlowForgeScreen(this)));
    }

    public void openEditorForDefinition(int definitionId) {
        ensureInitialized();
        if (getDefinitionById(definitionId) == null) return;

        selectDefinition(definitionId);
        openEditor();
    }

    public void openManager() {
        ensureInitialized();
        if (mc == null) return;
        mc.execute(() -> mc.setScreen(new FlowModuleManagerScreen(this)));
    }

    public void ensureHiddenFromClickGui() {
        Config config = Config.get();
        if (config == null) return;

        List<Module> hiddenModules = config.hiddenModules.get();
        if (hiddenModules.contains(this)) return;

        hiddenModules.add(this);
        config.hiddenModules.onChanged();
    }

    public List<CustomFlowDefinition> getDefinitions() {
        ensureInitialized();
        return definitions;
    }

    public CustomFlowDefinition getSelectedDefinition() {
        ensureInitialized();

        CustomFlowDefinition selected = findDefinitionByIdNoInit(selectedDefinitionId);
        if (selected != null) return selected;

        reloadCacheFromService();
        selected = findDefinitionByIdNoInit(selectedDefinitionId);
        if (selected != null) return selected;

        if (definitions.isEmpty()) {
            createDefinition();
            selected = findDefinitionByIdNoInit(selectedDefinitionId);
            if (selected != null) return selected;
        }

        selectedDefinitionId = definitions.isEmpty() ? -1 : definitions.getFirst().id;
        return definitions.isEmpty() ? null : definitions.getFirst();
    }

    public CustomFlowDefinition getDefinitionById(int definitionId) {
        ensureInitialized();
        return findDefinitionByIdNoInit(definitionId);
    }

    public void selectDefinition(int definitionId) {
        ensureInitialized();
        if (findDefinitionByIdNoInit(definitionId) == null) return;

        v2Kernel.registryService().selectDefinition(definitionId);
        reloadCacheFromService();
    }

    public void selectPreviousDefinition() {
        ensureInitialized();

        v2Kernel.registryService().selectPreviousDefinition();
        reloadCacheFromService();
    }

    public void selectNextDefinition() {
        ensureInitialized();

        v2Kernel.registryService().selectNextDefinition();
        reloadCacheFromService();
    }

    public CustomFlowDefinition createDefinition() {
        ensureInitialized();

        FlowDefinition created = v2Kernel.registryService().createDefinition("custom-module");
        reloadCacheFromService();

        CustomFlowDefinition definition = findDefinitionByIdNoInit(created.id());
        return definition == null ? getSelectedDefinition() : definition;
    }

    public boolean deleteSelectedDefinition() {
        return deleteDefinition(selectedDefinitionId);
    }

    public boolean deleteDefinition(int definitionId) {
        ensureInitialized();

        boolean removed = v2Kernel.registryService().deleteDefinition(definitionId);
        if (!removed) return false;

        reloadCacheFromService();
        return true;
    }

    public String renameDefinition(int definitionId, String preferredName) {
        ensureInitialized();

        FlowDefinition renamed = v2Kernel.registryService().renameDefinition(definitionId, preferredName);
        if (renamed == null) return null;

        reloadCacheFromService();
        CustomFlowDefinition updated = findDefinitionByIdNoInit(definitionId);
        return updated == null ? renamed.name() : updated.name;
    }

    public void setDefinitionAvailable(int definitionId, boolean available) {
        ensureInitialized();

        v2Kernel.registryService().setDefinitionAvailable(definitionId, available);
        reloadCacheFromService();
    }

    public void toggleDefinitionAvailable(int definitionId) {
        ensureInitialized();

        v2Kernel.registryService().toggleDefinitionAvailable(definitionId);
        reloadCacheFromService();
    }

    public void toggleDefinitionRunning(int definitionId) {
        ensureInitialized();

        v2Kernel.registryService().toggleDefinitionRunning(definitionId);
        reloadCacheFromService();
    }

    public int panicStopAllDefinitions() {
        ensureInitialized();
        int stopped = v2Kernel.registryService().stopAllRunningDefinitions();
        if (stopped > 0) reloadCacheFromService();
        return stopped;
    }

    public boolean isDefinitionRunning(int definitionId) {
        ensureInitialized();
        return v2Kernel.registryService().isDefinitionRunning(definitionId);
    }

    public void setSelectedDefinitionRunning(boolean running) {
        ensureInitialized();

        CustomFlowDefinition definition = getSelectedDefinition();
        if (definition == null || !definition.available) return;

        v2Kernel.registryService().setDefinitionRunning(definition.id, running);
        reloadCacheFromService();
    }

    public List<FlowNode> nodes() {
        CustomFlowDefinition definition = getSelectedDefinition();
        if (definition == null || definition.graph == null || definition.graph.nodes == null) return Collections.emptyList();
        return definition.graph.nodes;
    }

    public List<FlowLink> links() {
        CustomFlowDefinition definition = getSelectedDefinition();
        if (definition == null || definition.graph == null || definition.graph.links == null) return Collections.emptyList();
        return definition.graph.links;
    }

    public FlowNode getNodeById(int id) {
        for (FlowNode node : nodes()) {
            if (node.id == id) return node;
        }

        return null;
    }

    public FlowNode addNode(NodeKind kind, double x, double y) {
        CustomFlowDefinition definition = getSelectedDefinition();
        if (definition == null) return null;

        if (definition.graph == null) definition.graph = new GraphSnapshot();
        if (definition.graph.nodes == null) definition.graph.nodes = new ArrayList<>();

        FlowNode node = FlowNode.create(definition.graph.nextNodeId++, kind == null ? NodeKind.Notify : kind, x, y);
        definition.graph.nodes.add(node);

        markGraphDirty();
        return getNodeById(node.id);
    }

    public void removeNode(int nodeId) {
        CustomFlowDefinition definition = getSelectedDefinition();
        if (definition == null || definition.graph == null) return;

        definition.graph.nodes.removeIf(node -> node.id == nodeId);
        definition.graph.links.removeIf(link -> link.fromNodeId == nodeId || link.toNodeId == nodeId);
        markGraphDirty();
    }

    public void bringNodeToFront(int nodeId) {
        CustomFlowDefinition definition = getSelectedDefinition();
        if (definition == null || definition.graph == null || definition.graph.nodes == null) return;

        for (int i = 0; i < definition.graph.nodes.size(); i++) {
            if (definition.graph.nodes.get(i).id != nodeId) continue;

            if (i != definition.graph.nodes.size() - 1) {
                FlowNode node = definition.graph.nodes.remove(i);
                definition.graph.nodes.add(node);
                markGraphDirty();
            }
            return;
        }
    }

    public void addLink(int fromNodeId, int toNodeId) {
        if (fromNodeId == toNodeId) return;
        if (getNodeById(fromNodeId) == null || getNodeById(toNodeId) == null) return;

        List<FlowLink> links = links();
        for (FlowLink link : links) {
            if (link.fromNodeId == fromNodeId && link.toNodeId == toNodeId) return;
        }

        links.add(new FlowLink(fromNodeId, toNodeId));
        markGraphDirty();
    }

    public void removeLink(FlowLink link) {
        if (link == null) return;

        List<FlowLink> links = links();
        if (!links.remove(link)) return;

        markGraphDirty();
    }

    public void clearCurrentGraph() {
        CustomFlowDefinition definition = getSelectedDefinition();
        if (definition == null) return;

        if (definition.graph == null) definition.graph = new GraphSnapshot();
        definition.graph.nodes.clear();
        definition.graph.links.clear();
        definition.graph.nextNodeId = 1;

        markGraphDirty();
    }

    public void markGraphDirty() {
        ensureInitialized();

        CustomFlowDefinition definition = findDefinitionByIdNoInit(selectedDefinitionId);
        if (definition == null) return;

        if (definition.graph == null) definition.graph = new GraphSnapshot();

        if (autoCleanupOnSave.get()) {
            FlowGraphCleanup.cleanupInPlace(definition.graph, pruneUnreachableOnCleanup.get());
        } else {
            sanitizeLegacyGraph(definition.graph);
        }

        v2Kernel.registryService().replaceDefinitionGraph(definition.id, toDomainGraph(definition.graph));
        reloadCacheFromService();
    }

    public FlowGraphCleanup.Report cleanupSelectedGraph(boolean pruneUnreachableActions) {
        ensureInitialized();

        CustomFlowDefinition definition = findDefinitionByIdNoInit(selectedDefinitionId);
        if (definition == null) return FlowGraphCleanup.Report.empty();
        if (definition.graph == null) definition.graph = new GraphSnapshot();

        FlowGraphCleanup.Report report = FlowGraphCleanup.cleanupInPlace(definition.graph, pruneUnreachableActions);
        if (report.changed()) {
            v2Kernel.registryService().replaceDefinitionGraph(definition.id, toDomainGraph(definition.graph));
            reloadCacheFromService();
        }

        return report;
    }

    public FlowGraphCleanup.Report cleanupSelectedGraph() {
        return cleanupSelectedGraph(pruneUnreachableOnCleanup.get());
    }

    public FlowGraphComplexity.Result complexityOfSelectedDefinition() {
        return FlowGraphComplexity.analyze(getSelectedDefinition());
    }

    public FlowGraphComplexity.Result complexityOfDefinition(int definitionId) {
        return FlowGraphComplexity.analyze(getDefinitionById(definitionId));
    }

    public FlowRuntimeProfile runtimeProfileOfSelectedDefinition() {
        CustomFlowDefinition selected = getSelectedDefinition();
        if (selected == null) return FlowRuntimeProfile.empty(0, "");
        return v2Kernel.registryService().runtimeProfile(selected.id);
    }

    public FlowRuntimeProfile runtimeProfile(int definitionId) {
        return v2Kernel.registryService().runtimeProfile(definitionId);
    }

    public void resetRuntimeProfile(int definitionId) {
        v2Kernel.registryService().resetRuntimeProfile(definitionId);
    }

    public int resetAllRuntimeProfiles() {
        ensureInitialized();
        return v2Kernel.registryService().resetAllRuntimeProfiles();
    }

    public FlowPresetCodec.ExportResult exportSelectedPreset() {
        ensureInitialized();
        return FlowPresetCodec.export(v2Kernel.registryService().getSelectedDefinition(), presetSigningKey.get());
    }

    public boolean copySelectedPresetToClipboard() {
        if (mc == null || mc.keyboard == null) return false;

        FlowPresetCodec.ExportResult exported = exportSelectedPreset();
        if (!exported.success()) return false;

        mc.keyboard.setClipboard(exported.payload());
        return true;
    }

    public PresetImportResult importPresetFromClipboard(boolean replaceSelected) {
        if (mc == null || mc.keyboard == null) return PresetImportResult.failure("Clipboard is not available.");
        return importPreset(mc.keyboard.getClipboard(), replaceSelected);
    }

    public PresetImportResult importPreset(String rawPreset, boolean replaceSelected) {
        ensureInitialized();

        FlowPresetCodec.ImportResult imported = FlowPresetCodec.importPreset(rawPreset, presetSigningKey.get());
        if (!imported.success()) return PresetImportResult.failure(imported.message());
        if (!imported.checksumValid()) return PresetImportResult.failure(imported.message());

        if (enforceSignedPresets.get()) {
            if (!imported.signed()) return PresetImportResult.failure("Signed presets are required by policy.");
            if (!imported.signatureVerified()) return PresetImportResult.failure("Preset signature verification failed.");
        }

        FlowDefinition presetDefinition = imported.definition();
        if (presetDefinition == null) return PresetImportResult.failure("Preset payload does not contain a valid definition.");

        FlowRegistryService service = v2Kernel.registryService();
        int targetDefinitionId;

        if (replaceSelected) {
            FlowDefinition selected = service.getSelectedDefinition();
            targetDefinitionId = selected.id();
            service.replaceDefinitionGraph(targetDefinitionId, presetDefinition.graph());
            service.selectDefinition(targetDefinitionId);
        } else {
            FlowDefinition created = service.createDefinition(presetDefinition.name());
            targetDefinitionId = created.id();
            service.replaceDefinitionGraph(targetDefinitionId, presetDefinition.graph());
            service.setDefinitionAvailable(targetDefinitionId, presetDefinition.available());
            service.selectDefinition(targetDefinitionId);
        }

        reloadCacheFromService();
        return PresetImportResult.success(targetDefinitionId, imported.signed(), imported.signatureVerified(), imported.message());
    }

    public int maxActionsBudget() {
        return Math.max(1, maxActionsPerTick.get());
    }

    public boolean isCaseInsensitiveChatMatch() {
        return caseInsensitiveChatMatch.get();
    }

    private void reloadCacheFromService() {
        List<FlowDefinition> domainDefinitions = v2Kernel.registryService().getDefinitions();

        definitions.clear();
        for (FlowDefinition definition : domainDefinitions) {
            definitions.add(toLegacyDefinition(definition));
        }

        FlowDefinition selected = v2Kernel.registryService().getSelectedDefinition();
        selectedDefinitionId = selected == null ? -1 : selected.id();

        if (findDefinitionByIdNoInit(selectedDefinitionId) == null && !definitions.isEmpty()) {
            selectedDefinitionId = definitions.getFirst().id;
        }
    }

    private CustomFlowDefinition findDefinitionByIdNoInit(int definitionId) {
        for (CustomFlowDefinition definition : definitions) {
            if (definition.id == definitionId) return definition;
        }

        return null;
    }

    private CustomFlowDefinition toLegacyDefinition(FlowDefinition definition) {
        CustomFlowDefinition legacy = new CustomFlowDefinition();
        legacy.id = definition.id();
        legacy.name = definition.name();
        legacy.available = definition.available();
        legacy.active = definition.active();

        GraphSnapshot graph = new GraphSnapshot();
        if (definition.graph() != null) {
            graph.nextNodeId = Math.max(1, definition.graph().nextNodeId());

            for (com.kyv3.addon.modules.flow.v2.domain.FlowNode node : definition.graph().nodesView()) {
                FlowNode legacyNode = new FlowNode();
                legacyNode.id = node.id();
                legacyNode.kind = toLegacyKind(node.kind());
                legacyNode.x = node.x();
                legacyNode.y = node.y();
                legacyNode.text = node.text();
                legacyNode.number = Math.max(1, node.number());
                graph.nodes.add(legacyNode);
            }

            for (com.kyv3.addon.modules.flow.v2.domain.FlowLink link : definition.graph().linksView()) {
                graph.links.add(new FlowLink(link.fromNodeId(), link.toNodeId()));
            }
        }

        sanitizeLegacyGraph(graph);
        legacy.graph = graph;
        return legacy;
    }

    private com.kyv3.addon.modules.flow.v2.domain.FlowGraph toDomainGraph(GraphSnapshot graph) {
        sanitizeLegacyGraph(graph);

        List<com.kyv3.addon.modules.flow.v2.domain.FlowNode> nodes = new ArrayList<>(graph.nodes.size());
        for (FlowNode node : graph.nodes) {
            if (node == null || node.id <= 0) continue;

            nodes.add(new com.kyv3.addon.modules.flow.v2.domain.FlowNode(
                node.id,
                toDomainKind(node.kind),
                node.x,
                node.y,
                node.text,
                node.number
            ));
        }

        List<com.kyv3.addon.modules.flow.v2.domain.FlowLink> links = new ArrayList<>(graph.links.size());
        for (FlowLink link : graph.links) {
            if (link == null) continue;
            links.add(new com.kyv3.addon.modules.flow.v2.domain.FlowLink(link.fromNodeId, link.toNodeId));
        }

        com.kyv3.addon.modules.flow.v2.domain.FlowGraph domainGraph = new com.kyv3.addon.modules.flow.v2.domain.FlowGraph(
            Math.max(1, graph.nextNodeId),
            nodes,
            links
        );
        domainGraph.sanitize();

        return domainGraph;
    }

    private void sanitizeLegacyGraph(GraphSnapshot graph) {
        FlowGraphCleanup.cleanupInPlace(graph, false);
    }

    private static FlowNodeKind toDomainKind(NodeKind legacyKind) {
        if (legacyKind == null) return FlowNodeKind.Notify;

        try {
            return FlowNodeKind.valueOf(legacyKind.name());
        } catch (IllegalArgumentException ignored) {
            return FlowNodeKind.Notify;
        }
    }

    private static NodeKind toLegacyKind(FlowNodeKind domainKind) {
        if (domainKind == null) return NodeKind.Notify;

        try {
            return NodeKind.valueOf(domainKind.name());
        } catch (IllegalArgumentException ignored) {
            return NodeKind.Notify;
        }
    }

    public enum NodeKind {
        OnEnable(true, false, false, "On Enable"),
        OnJoin(true, false, false, "On Join"),
        OnTick(true, false, true, "On Tick"),
        OnChatMatch(true, true, false, "On Chat Match"),

        SendMessage(false, true, false, "Send Message"),
        SendCommand(false, true, false, "Send Command"),
        Notify(false, true, false, "Notify"),
        Wait(false, false, true, "Wait"),
        ToggleModule(false, true, false, "Toggle Module"),
        EnableModule(false, true, false, "Enable Module"),
        DisableModule(false, true, false, "Disable Module"),
        Chance(false, false, true, "Chance %"),
        Repeat(false, false, true, "Repeat"),

        IfHealthBelow(false, false, true, "If Health <= "),
        IfHealthAbove(false, false, true, "If Health >= "),
        IfHungerBelow(false, false, true, "If Hunger <= "),
        IfOnGround(false, false, false, "If On Ground"),
        IfInWater(false, false, false, "If In Water"),
        IfSneaking(false, false, false, "If Sneaking"),
        IfSprinting(false, false, false, "If Sprinting"),
        IfMoving(false, false, true, "If Speed > "),
        IfModuleActive(false, true, false, "If Module Active"),
        IfHoldingItem(false, true, false, "If Holding Item"),
        IfDimensionContains(false, true, false, "If Dimension Contains"),
        IfTargetEntityContains(false, true, false, "If Target Contains"),

        Jump(false, false, false, "Jump"),
        SneakOn(false, false, false, "Sneak ON"),
        SneakOff(false, false, false, "Sneak OFF"),
        SprintOn(false, false, false, "Sprint ON"),
        SprintOff(false, false, false, "Sprint OFF"),
        UseMainHand(false, false, false, "Use Main Hand"),
        UseOffHand(false, false, false, "Use Off Hand"),
        SwingMainHand(false, false, false, "Swing Main Hand"),
        SwingOffHand(false, false, false, "Swing Off Hand"),
        AttackTargetEntity(false, false, false, "Attack Target"),
        DropSelectedStack(false, false, false, "Drop Stack"),
        DropSingleItem(false, false, false, "Drop 1 Item"),
        SwapHands(false, false, false, "Swap Hands"),
        OpenInventory(false, false, false, "Open Inventory"),
        CloseCurrentScreen(false, false, false, "Close Screen"),
        SelectHotbar(false, false, true, "Select Hotbar"),
        SelectNextHotbar(false, false, false, "Next Hotbar"),
        SelectPreviousHotbar(false, false, false, "Prev Hotbar"),
        LookYaw(false, false, true, "Set Yaw"),
        LookPitch(false, false, true, "Set Pitch"),
        AddForwardVelocity(false, false, true, "Boost Forward"),
        AddVerticalVelocity(false, false, true, "Boost Vertical"),
        StopMotion(false, false, false, "Stop Motion");

        private final boolean event;
        private final boolean supportsText;
        private final boolean supportsNumber;
        public final String title;

        NodeKind(boolean event, boolean supportsText, boolean supportsNumber, String title) {
            this.event = event;
            this.supportsText = supportsText;
            this.supportsNumber = supportsNumber;
            this.title = title;
        }

        public boolean isEvent() {
            return event;
        }

        public boolean supportsText() {
            return supportsText;
        }

        public boolean supportsNumber() {
            return supportsNumber;
        }
    }

    public static final class FlowNode {
        public int id;
        public NodeKind kind;
        public double x;
        public double y;
        public String text;
        public int number;

        public static FlowNode create(int id, NodeKind kind, double x, double y) {
            FlowNode node = new FlowNode();
            node.id = id;
            node.kind = kind;
            node.x = x;
            node.y = y;
            node.number = 1;
            node.text = "";

            switch (kind) {
                case OnTick -> node.number = 20;
                case OnChatMatch -> node.text = "vote";
                case Wait -> node.number = 20;
                case SendMessage -> node.text = "/spawn";
                case SendCommand -> node.text = "spawn";
                case Notify -> node.text = "Custom module executed.";
                case ToggleModule, EnableModule, DisableModule -> node.text = "auto-login";
                case Chance -> node.number = 50;
                case Repeat -> node.number = 2;
                case IfHealthBelow -> node.number = 10;
                case IfHealthAbove -> node.number = 10;
                case IfHungerBelow -> node.number = 14;
                case IfMoving -> node.number = 10;
                case IfModuleActive -> node.text = "auto-login";
                case IfHoldingItem -> node.text = "totem";
                case IfDimensionContains -> node.text = "overworld";
                case IfTargetEntityContains -> node.text = "player";
                case SelectHotbar -> node.number = 1;
                case LookYaw -> node.number = 0;
                case LookPitch -> node.number = 0;
                case AddForwardVelocity -> node.number = 30;
                case AddVerticalVelocity -> node.number = 25;
                default -> {
                }
            }

            return node;
        }
    }

    public static final class FlowLink {
        public int fromNodeId;
        public int toNodeId;

        public FlowLink(int fromNodeId, int toNodeId) {
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
        }
    }

    public static final class GraphSnapshot {
        public int nextNodeId = 1;
        public List<FlowNode> nodes = new ArrayList<>();
        public List<FlowLink> links = new ArrayList<>();
    }

    public static final class CustomFlowDefinition {
        public int id;
        public String name;
        public boolean available;
        public boolean active;
        public GraphSnapshot graph;
    }

    public static final class PresetImportResult {
        private final boolean success;
        private final int definitionId;
        private final boolean signed;
        private final boolean signatureVerified;
        private final String message;

        private PresetImportResult(boolean success, int definitionId, boolean signed, boolean signatureVerified, String message) {
            this.success = success;
            this.definitionId = definitionId;
            this.signed = signed;
            this.signatureVerified = signatureVerified;
            this.message = message == null ? "" : message;
        }

        public static PresetImportResult success(int definitionId, boolean signed, boolean signatureVerified, String message) {
            return new PresetImportResult(true, definitionId, signed, signatureVerified, message);
        }

        public static PresetImportResult failure(String message) {
            return new PresetImportResult(false, -1, false, false, message);
        }

        public boolean success() {
            return success;
        }

        public int definitionId() {
            return definitionId;
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
}
