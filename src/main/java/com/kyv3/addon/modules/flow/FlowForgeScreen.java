package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.CustomFlowDefinition;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowLink;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowNode;
import com.kyv3.addon.modules.flow.FlowForgeModule.NodeKind;
import com.kyv3.addon.modules.flow.FlowGraphDiagnostics.Result;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeProfile;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FlowForgeScreen extends Screen {
    private static final int NODE_WIDTH = 186;
    private static final int NODE_HEIGHT = 82;
    private static final int PORT_SIZE = 8;
    private static final int GRID_SIZE = 20;

    private static final int BUTTON_WIDTH = 106;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_PADDING = 4;

    private static final int BOTTOM_PANEL_HEIGHT = 118;
    private static final int HISTORY_LIMIT = 80;

    private final FlowForgeModule module;

    private int toolbarBottom = 34;

    private final Set<Integer> selectedNodeIds = new LinkedHashSet<>();
    private int primarySelectedNodeId = -1;

    private Integer linkingFromNodeId;
    private boolean editingText;
    private boolean textEditHistoryCaptured;
    private boolean textEditMutated;

    private boolean draggingNodes;
    private boolean dragMutationCaptured;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private final Map<Integer, Vec2> dragStartPositions = new HashMap<>();

    private boolean boxSelecting;
    private boolean boxSelectAdditive;
    private double boxStartX;
    private double boxStartY;
    private double boxEndX;
    private double boxEndY;

    private boolean panning;
    private double panStartMouseX;
    private double panStartMouseY;
    private double panStartCameraX;
    private double panStartCameraY;

    private boolean cameraInitialized;
    private double cameraX;
    private double cameraY;
    private double zoom = 1.0;

    private double lastMouseX;
    private double lastMouseY;

    private final Deque<GraphHistoryState> undoStack = new ArrayDeque<>();
    private final Deque<GraphHistoryState> redoStack = new ArrayDeque<>();
    private int historyDefinitionId = -1;

    private ClipboardGraph clipboard;

    private String paletteFilter = "";
    private boolean paletteFilterFocused;
    private int paletteCursorIndex;
    private long paletteCursorBlinkStartedAt;
    private int paletteScroll;
    private int paletteSearchX;
    private int paletteSearchY;
    private int paletteSearchW;
    private int paletteSearchH;
    private int paletteListX1;
    private int paletteListY1;
    private int paletteListX2;
    private int paletteListY2;
    private final List<NodeKind> paletteKinds = new ArrayList<>();
    private final List<PaletteRow> paletteRows = new ArrayList<>();
    private final Map<NodeKind, Float> paletteRowHoverProgress = new HashMap<>();
    private final Map<Integer, Float> nodeHoverProgress = new HashMap<>();
    private final List<ToolbarControl> toolbarControls = new ArrayList<>();
    private final Map<String, Float> toolbarHoverProgress = new HashMap<>();
    private int toolbarLayoutWidth = -1;
    private int toolbarLayoutHeight = -1;
    private float paletteSearchHoverProgress;

    private boolean validationDirty = true;
    private Result diagnostics = FlowGraphDiagnostics.Result.empty();
    private final Set<Integer> warningNodeIds = new HashSet<>();
    private final List<String> validationMessages = new ArrayList<>();
    private String transientStatusMessage = "";
    private long transientStatusUntil;

    public FlowForgeScreen(FlowForgeModule module) {
        super(Text.literal("Kyv3 Flow Forge"));
        this.module = module;
    }

    @Override
    protected void init() {
        clearChildren();
        buildToolbarControls();

        if (!cameraInitialized) {
            cameraX = canvasCenterX();
            cameraY = canvasCenterY();
            cameraInitialized = true;
        }

        paletteCursorIndex = paletteFilter.length();
        resetPaletteCursorBlink();

        ensureHistoryBoundToCurrentDefinition();
    }

    private void ensureToolbarLayout() {
        if (toolbarLayoutWidth == width && toolbarLayoutHeight == height && !toolbarControls.isEmpty()) return;
        buildToolbarControls();
    }

    private void buildToolbarControls() {
        toolbarControls.clear();
        int[] cursor = new int[] { 12, 10 };

        addToolbarControl("prev-module", "Prev Module", () -> {
            module.selectPreviousDefinition();
            onDefinitionChanged();
        }, cursor, 0xFF355F86, 0xFFCDE6FF);
        addToolbarControl("next-module", "Next Module", () -> {
            module.selectNextDefinition();
            onDefinitionChanged();
        }, cursor, 0xFF355F86, 0xFFCDE6FF);
        addToolbarControl("new-module", "New Module", () -> {
            module.createDefinition();
            onDefinitionChanged();
        }, cursor, 0xFF306A54, 0xFFADEFD1);
        addToolbarControl("delete-module", "Delete Module", () -> {
            module.deleteSelectedDefinition();
            onDefinitionChanged();
        }, cursor, 0xFF73333B, 0xFFFFC6CE);

        addToolbarControl("toggle-gui", "Toggle GUI", this::toggleModuleAvailability, cursor, 0xFF2F5677, 0xFFC7DEFA);
        addToolbarControl("toggle-run", "Toggle Run", this::toggleModuleRunning, cursor, 0xFF2F6153, 0xFFA5F2D7);
        addToolbarControl("panic-stop", "Panic Stop", this::panicStopAll, cursor, 0xFF7A313C, 0xFFFFC2CB);
        addToolbarControl("reset-runtime", "Reset Runtime Stats", this::resetAllRuntimeStats, cursor, 0xFF2E5D7A, 0xFFC8E6FF);
        addToolbarControl("open-manager", "Open Manager", module::openManager, cursor, 0xFF41577A, 0xFFD7E4F6);

        addToolbarControl("save", "Save", module::markGraphDirty, cursor, 0xFF3E5E7D, 0xFFCFE4FF);
        addToolbarControl("auto-cleanup", "Auto Cleanup", this::autoCleanupGraph, cursor, 0xFF2F6B58, 0xFFAEEFCC);
        addToolbarControl("undo", "Undo", this::undo, cursor, 0xFF4B5177, 0xFFD5D9FF);
        addToolbarControl("redo", "Redo", this::redo, cursor, 0xFF4B5177, 0xFFD5D9FF);
        addToolbarControl("copy", "Copy", this::copySelection, cursor, 0xFF3C5B83, 0xFFC9DEFF);
        addToolbarControl("paste", "Paste", this::pasteClipboard, cursor, 0xFF3C5B83, 0xFFC9DEFF);
        addToolbarControl("export-preset", "Export Preset", this::exportPresetToClipboard, cursor, 0xFF5E4B7F, 0xFFE3D9FF);
        addToolbarControl("import-preset", "Import Preset", this::importPresetFromClipboard, cursor, 0xFF5E4B7F, 0xFFE3D9FF);
        addToolbarControl("clear-graph", "Clear Graph", this::clearGraph, cursor, 0xFF6F3B3B, 0xFFFFC9C9);
        addToolbarControl("reset-view", "Reset View", this::resetView, cursor, 0xFF365673, 0xFFCBE4FB);

        toolbarBottom = cursor[1] + BUTTON_HEIGHT;
        toolbarLayoutWidth = width;
        toolbarLayoutHeight = height;
    }

    private void addToolbarControl(String id, String label, Runnable action, int[] cursor, int baseColor, int textColor) {
        int buttonWidth = toolbarButtonWidth(label);
        if (cursor[0] + buttonWidth > width - 12) {
            cursor[0] = 12;
            cursor[1] += BUTTON_HEIGHT + BUTTON_PADDING;
        }

        toolbarControls.add(new ToolbarControl(id, label, cursor[0], cursor[1], buttonWidth, BUTTON_HEIGHT, baseColor, textColor, action));
        cursor[0] += buttonWidth + BUTTON_PADDING;
    }

    private int toolbarButtonWidth(String label) {
        int textWidth = textRenderer == null ? BUTTON_WIDTH : textRenderer.getWidth(label) + 18;
        return MathHelper.clamp(textWidth, 78, 158);
    }

    private void onDefinitionChanged() {
        selectedNodeIds.clear();
        primarySelectedNodeId = -1;
        linkingFromNodeId = null;
        editingText = false;
        textEditHistoryCaptured = false;
        textEditMutated = false;
        draggingNodes = false;
        panning = false;
        boxSelecting = false;
        paletteFilterFocused = false;
        paletteCursorIndex = paletteFilter.length();
        resetPaletteCursorBlink();

        undoStack.clear();
        redoStack.clear();
        historyDefinitionId = module.getSelectedDefinition().id;

        validationDirty = true;
        diagnostics = FlowGraphDiagnostics.Result.empty();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        ensureToolbarLayout();
        ensureHistoryBoundToCurrentDefinition();
        sanitizeSelection();
        refreshValidationIfNeeded();
        pruneHoverState();

        long time = System.currentTimeMillis();
        int glow = 18 + (int) (10 * (0.5 + 0.5 * Math.sin(time / 420.0)));

        context.fillGradient(0, 0, width, height, FlowGuiStyle.ROOT_BG_TOP, FlowGuiStyle.ROOT_BG_BOTTOM);
        context.fillGradient(0, 0, width, 72, FlowGuiStyle.ROOT_VIGNETTE_TOP, FlowGuiStyle.ROOT_VIGNETTE_BOTTOM);
        context.fill(0, 0, glow, height, 0x22000000);
        context.fill(width - glow, 0, width, height, 0x22000000);

        renderToolbarBackdrop(context, mouseX, mouseY, delta);
        renderCanvas(context, mouseX, mouseY, delta);
        renderPalettePanel(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        renderBottomPanel(context, mouseX, mouseY, delta);
    }

    private void renderToolbarBackdrop(DrawContext context, int mouseX, int mouseY, float delta) {
        int top = 6;
        int bottom = toolbarBottom + 8;
        context.fillGradient(6, top, width - 6, bottom, FlowGuiStyle.PANEL_TOP, FlowGuiStyle.PANEL_BOTTOM);
        drawRectOutline(context, 6, top, width - 12, bottom - top, FlowGuiStyle.PANEL_BORDER);
        context.fillGradient(7, top + 1, width - 7, top + 18, 0x3FFFFFFF, 0x00FFFFFF);

        for (ToolbarControl control : toolbarControls) {
            drawToolbarControl(context, control, mouseX, mouseY, delta);
        }
    }

    private void drawToolbarControl(DrawContext context, ToolbarControl control, int mouseX, int mouseY, float delta) {
        boolean hovered = isInside(mouseX, mouseY, control.x, control.y, control.w, control.h);
        float hoverProgress = animateHover(toolbarHoverProgress, control.id, hovered, delta, 11f);

        int bg = blendColor(control.baseColor, brighten(control.baseColor, 0.18f), hoverProgress);
        int border = blendColor(0xFF111A24, 0xFFA5CEEF, hoverProgress);
        int topGlow = FlowGuiStyle.alpha(0xFFFFFF, (int) (26 * hoverProgress));

        context.fill(control.x, control.y, control.x + control.w, control.y + control.h, bg);
        context.fill(control.x + 1, control.y + 1, control.x + control.w - 1, control.y + 6, topGlow);
        drawRectOutline(context, control.x, control.y, control.w, control.h, border);

        int textX = control.x + (control.w - textRenderer.getWidth(control.label)) / 2;
        int textY = control.y + (control.h - 8) / 2;
        int text = blendColor(control.textColor, 0xFFFFFFFF, hoverProgress * 0.16f);
        context.drawTextWithShadow(textRenderer, control.label, textX, textY, text);
    }

    private void renderCanvas(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = canvasLeft();
        int top = canvasTop();
        int right = canvasRight();
        int bottom = canvasBottom();

        context.fillGradient(left, top, right, bottom, 0xBE0F151D, 0xBE0A0D12);
        drawGrid(context, left, top, right, bottom);
        context.fillGradient(left + 1, top + 1, right - 1, top + 36, 0xB4243346, 0x3A243346);
        drawRectOutline(context, left, top, right - left, bottom - top, FlowGuiStyle.PANEL_BORDER);

        CustomFlowDefinition definition = module.getSelectedDefinition();
        String status = (definition.available ? "GUI ON" : "GUI OFF") + " | " + (module.isDefinitionRunning(definition.id) ? "RUNNING" : "STOPPED");
        context.drawTextWithShadow(textRenderer, "FlowForge > " + definition.name + "  [" + status + "]", left + 8, top + 7, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "Zoom: " + formatZoom() + " | Pan: " + (int) cameraX + "," + (int) cameraY, left + 8, top + 20, FlowGuiStyle.TEXT_SECONDARY);

        for (FlowLink link : module.links()) {
            FlowNode from = module.getNodeById(link.fromNodeId);
            FlowNode to = module.getNodeById(link.toNodeId);
            if (from == null || to == null) continue;

            NodeRect fromRect = rectForNode(from);
            NodeRect toRect = rectForNode(to);

            int x1 = outputCenterX(fromRect);
            int y1 = portCenterY(fromRect);
            int x2 = inputCenterX(toRect);
            int y2 = portCenterY(toRect);

            boolean selectedLink = selectedNodeIds.contains(from.id) || selectedNodeIds.contains(to.id);
            drawCable(context, x1, y1, x2, y2, selectedLink ? 0xFF96D8FF : 0xFF6EA6E8, selectedLink ? 0x5F3CA8FF : 0x304A89C7);
        }

        if (linkingFromNodeId != null) {
            FlowNode from = module.getNodeById(linkingFromNodeId);
            if (from != null) {
                NodeRect fromRect = rectForNode(from);
                drawCable(context, outputCenterX(fromRect), portCenterY(fromRect), mouseX, mouseY, 0xFF74F7AC, 0x5050F28A);
            }
        }

        FlowNode hoveredNode = findNodeAt(mouseX, mouseY);
        int hoveredNodeId = hoveredNode == null ? -1 : hoveredNode.id;
        List<FlowNode> nodes = module.nodes();
        for (FlowNode node : nodes) {
            float hoverProgress = animateHover(nodeHoverProgress, node.id, node.id == hoveredNodeId, delta, 8f);
            renderNode(context, node, hoverProgress);
        }

        if (boxSelecting) {
            int x1 = (int) Math.min(boxStartX, boxEndX);
            int y1 = (int) Math.min(boxStartY, boxEndY);
            int x2 = (int) Math.max(boxStartX, boxEndX);
            int y2 = (int) Math.max(boxStartY, boxEndY);
            context.fill(x1, y1, x2, y2, 0x2C88B4E8);
            drawRectOutline(context, x1, y1, x2 - x1, y2 - y1, 0xFF8EC4FF);
        }
    }

    private void renderNode(DrawContext context, FlowNode node, float hoverProgress) {
        NodeRect rect = rectForNode(node);

        boolean selected = selectedNodeIds.contains(node.id);
        boolean primary = primarySelectedNodeId == node.id;
        boolean condition = node.kind.name().startsWith("If");
        boolean warning = warningNodeIds.contains(node.id);

        int bodyTop;
        int bodyBottom;
        int headerTop;
        int headerBottom;
        int accent;
        String badge;

        if (node.kind.isEvent()) {
            bodyTop = 0xD81A3550;
            bodyBottom = 0xD8132538;
            headerTop = 0xFF2E7AAF;
            headerBottom = 0xFF245D88;
            accent = 0xFF87D9FF;
            badge = "EVENT";
        } else if (condition) {
            bodyTop = 0xD84E3E15;
            bodyBottom = 0xD83A2D10;
            headerTop = 0xFF9C7522;
            headerBottom = 0xFF7B5D1E;
            accent = 0xFFFFD68A;
            badge = "COND";
        } else {
            bodyTop = 0xD8272F37;
            bodyBottom = 0xD81E242B;
            headerTop = 0xFF4D6A85;
            headerBottom = 0xFF3B556D;
            accent = 0xFFA0C8F0;
            badge = "ACTION";
        }

        if (selected) {
            headerTop = blendColor(headerTop, 0xFFFFFFFF, primary ? 0.26f : 0.15f);
            accent = blendColor(accent, 0xFFFFFFFF, primary ? 0.34f : 0.2f);
        }

        if (hoverProgress > 0f) {
            bodyTop = blendColor(bodyTop, brighten(bodyTop, 0.2f), hoverProgress * 0.65f);
            bodyBottom = blendColor(bodyBottom, brighten(bodyBottom, 0.15f), hoverProgress * 0.65f);
            headerTop = blendColor(headerTop, brighten(headerTop, 0.24f), hoverProgress * 0.75f);
            headerBottom = blendColor(headerBottom, brighten(headerBottom, 0.2f), hoverProgress * 0.75f);
            accent = blendColor(accent, 0xFFFFFFFF, hoverProgress * 0.2f);
        }

        if (warning) {
            accent = blendColor(accent, 0xFFFF8765, 0.45f);
        }

        int shadowColor;
        if (primary) shadowColor = 0x6A3966A1;
        else if (selected) shadowColor = 0x5A1A436A;
        else shadowColor = 0x36000000;
        shadowColor = blendColor(shadowColor, 0x6A2C5F8B, hoverProgress * 0.45f);

        context.fill(rect.x - 2, rect.y - 2, rect.x + rect.w + 2, rect.y + rect.h + 2, shadowColor);
        context.fillGradient(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, bodyTop, bodyBottom);
        context.fillGradient(rect.x + 1, rect.y + 1, rect.x + rect.w - 1, rect.y + Math.max(18, scale(19)), headerTop, headerBottom);
        context.fill(rect.x + 1, rect.y + Math.max(18, scale(19)), rect.x + rect.w - 1, rect.y + Math.max(19, scale(20)), accent);

        int border = warning ? 0xFFE3774F : primary ? 0xFF91DCFF : selected ? 0xFF76BCE8 : 0xFF0C1117;
        border = blendColor(border, 0xFF9FD7FF, hoverProgress * 0.55f);
        drawRectOutline(context, rect.x, rect.y, rect.w, rect.h, border);

        int textX = rect.x + scale(6);
        int titleY = rect.y + scale(5);

        context.drawTextWithShadow(textRenderer, node.kind.title, textX, titleY, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "#" + node.id, rect.x + rect.w - scale(28), titleY, 0xFFE6EEF7);

        if (rect.w >= 110 && rect.h >= 52) {
            context.drawTextWithShadow(textRenderer, badge, textX, rect.y + rect.h - scale(11), accent);

            int lineY = rect.y + scale(24);
            if (node.kind.supportsText()) {
                String value = node.text == null ? "" : node.text;
                String preview = value.isEmpty() ? "text: <empty>" : "text: " + trimForNode(value, rect.w - scale(14));
                context.drawTextWithShadow(textRenderer, preview, textX, lineY, 0xFFE5EDF6);
                lineY += 11;
            }

            if (node.kind.supportsNumber()) {
                context.drawTextWithShadow(textRenderer, "value: " + node.number, textX, lineY, blendColor(accent, 0xFFFFFFFF, 0.15f));
            }
        }

        int portY = portTopY(rect);
        int inX = inputPortX(rect);
        int outX = outputPortX(rect);
        int port = scaledPortSize();
        int pulse = 40 + (int) (35 * (0.5 + 0.5 * Math.sin((System.currentTimeMillis() + node.id * 47L) / 180.0)));

        context.fill(inX - 1, portY - 1, inX + port + 1, portY + port + 1, selected ? (0x50000000 | (pulse << 16)) : 0x44000000);
        context.fill(inX, portY, inX + port, portY + port, 0xFFE45D62);
        context.fill(inX + 2, portY + 2, inX + port - 2, portY + port - 2, 0xFFFFB8BA);

        context.fill(outX - 1, portY - 1, outX + port + 1, portY + port + 1, (linkingFromNodeId != null && linkingFromNodeId == node.id) ? 0x5A51F28E : 0x44000000);
        context.fill(outX, portY, outX + port, portY + port, 0xFF57DA82);
        context.fill(outX + 2, portY + 2, outX + port - 2, portY + port - 2, 0xFFBDF8CF);

        if (warning) {
            context.drawTextWithShadow(textRenderer, "!", rect.x + rect.w - scale(12), rect.y + rect.h - scale(13), 0xFFFF9C70);
        }
    }

    private void renderPalettePanel(DrawContext context, int mouseX, int mouseY, float delta) {
        int x1 = palettePanelX1();
        int y1 = canvasTop();
        int x2 = palettePanelX2();
        int y2 = canvasBottom();

        context.fillGradient(x1, y1, x2, y2, FlowGuiStyle.PANEL_TOP, FlowGuiStyle.PANEL_BOTTOM);
        drawRectOutline(context, x1, y1, x2 - x1, y2 - y1, FlowGuiStyle.PANEL_BORDER);

        context.drawTextWithShadow(textRenderer, "Node Palette", x1 + 8, y1 + 7, FlowGuiStyle.TEXT_PRIMARY);

        paletteSearchX = x1 + 8;
        paletteSearchY = y1 + 20;
        paletteSearchW = Math.max(80, x2 - x1 - 16);
        paletteSearchH = 16;

        boolean searchHovered = isInside(mouseX, mouseY, paletteSearchX, paletteSearchY, paletteSearchW, paletteSearchH);
        paletteSearchHoverProgress = FlowGuiStyle.animate(paletteSearchHoverProgress, searchHovered || paletteFilterFocused, delta, 9f);

        int searchBg = FlowGuiStyle.blend(FlowGuiStyle.INPUT_IDLE, FlowGuiStyle.INPUT_HOVER, paletteSearchHoverProgress);
        if (paletteFilterFocused) searchBg = FlowGuiStyle.blend(searchBg, FlowGuiStyle.INPUT_FOCUS, 0.82f);

        int searchBorder = FlowGuiStyle.blend(FlowGuiStyle.INPUT_BORDER_IDLE, FlowGuiStyle.INPUT_BORDER_HOVER, paletteSearchHoverProgress);
        if (paletteFilterFocused) searchBorder = FlowGuiStyle.blend(searchBorder, FlowGuiStyle.INPUT_BORDER_FOCUS, 0.9f);

        context.fill(paletteSearchX, paletteSearchY, paletteSearchX + paletteSearchW, paletteSearchY + paletteSearchH, searchBg);
        drawRectOutline(context, paletteSearchX, paletteSearchY, paletteSearchW, paletteSearchH, searchBorder);

        String display = paletteFilter.isEmpty() ? "Search node kind..." : paletteFilter;
        int displayColor = paletteFilter.isEmpty() ? FlowGuiStyle.TEXT_MUTED : FlowGuiStyle.TEXT_PRIMARY;
        context.drawTextWithShadow(textRenderer, display, paletteSearchX + 5, paletteSearchY + 4, displayColor);

        if (paletteFilterFocused && shouldBlinkCursor()) {
            int cursorX = paletteSearchX + 5;
            if (!paletteFilter.isEmpty()) {
                clampPaletteCursor();
                cursorX += textRenderer.getWidth(paletteFilter.substring(0, paletteCursorIndex));
            }
            if (cursorX < paletteSearchX + paletteSearchW - 4) {
                context.fill(cursorX, paletteSearchY + 3, cursorX + 1, paletteSearchY + paletteSearchH - 3, FlowGuiStyle.TEXT_PRIMARY);
            }
        }

        paletteListX1 = x1 + 8;
        paletteListY1 = y1 + 41;
        paletteListX2 = x2 - 8;
        paletteListY2 = y2 - 64;

        context.fill(paletteListX1, paletteListY1, paletteListX2, paletteListY2, FlowGuiStyle.SURFACE_SOFT);
        drawRectOutline(context, paletteListX1, paletteListY1, paletteListX2 - paletteListX1, paletteListY2 - paletteListY1, FlowGuiStyle.PANEL_BORDER);

        refreshPaletteKinds();
        paletteRows.clear();

        int rowH = 18;
        int visibleRows = Math.max(1, (paletteListY2 - paletteListY1 - 4) / rowH);
        int maxScroll = Math.max(0, paletteKinds.size() - visibleRows);
        paletteScroll = MathHelper.clamp(paletteScroll, 0, maxScroll);

        for (int i = 0; i < visibleRows; i++) {
            int index = i + paletteScroll;
            if (index >= paletteKinds.size()) break;

            NodeKind kind = paletteKinds.get(index);
            int rowY = paletteListY1 + 2 + i * rowH;
            boolean hovered = isInside(mouseX, mouseY, paletteListX1 + 1, rowY, paletteListX2 - paletteListX1 - 2, rowH - 1);

            float rowHover = animateHover(paletteRowHoverProgress, kind, hovered, delta, 10f);
            int rowBg = (index % 2 == 0) ? 0x66203446 : 0x66172836;
            rowBg = blendColor(rowBg, brighten(rowBg, 0.14f), rowHover);
            context.fill(paletteListX1 + 1, rowY, paletteListX2 - 1, rowY + rowH - 1, rowBg);

            int categoryColor;
            if (kind.isEvent()) categoryColor = 0xFF8AD9FF;
            else if (kind.name().startsWith("If")) categoryColor = 0xFFF8D58B;
            else categoryColor = 0xFFA5CAF0;

            context.fill(paletteListX1 + 4, rowY + 5, paletteListX1 + 8, rowY + rowH - 6, blendColor(categoryColor, 0xFFFFFFFF, rowHover * 0.2f));
            context.drawTextWithShadow(textRenderer, kind.title, paletteListX1 + 12, rowY + 5, blendColor(0xFFE4EFFA, 0xFFFFFFFF, rowHover * 0.16f));

            paletteRows.add(new PaletteRow(kind, paletteListX1 + 1, rowY, paletteListX2 - paletteListX1 - 2, rowH - 1));
        }

        drawPaletteScrollbar(context, rowH, visibleRows, maxScroll);

        int infoY = y2 - 55;
        context.drawTextWithShadow(textRenderer, "Palette entries: " + paletteKinds.size(), x1 + 8, infoY, FlowGuiStyle.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, "Click row to spawn at viewport center", x1 + 8, infoY + 12, 0xFFAEC2D8);
        context.drawTextWithShadow(textRenderer, "Ctrl+Wheel zoom | MMB pan | Ctrl+C / Ctrl+V", x1 + 8, infoY + 24, 0xFFAEC2D8);
    }

    private void refreshPaletteKinds() {
        paletteKinds.clear();

        String needle = paletteFilter.trim().toLowerCase(Locale.ROOT);
        for (NodeKind kind : NodeKind.values()) {
            if (needle.isEmpty()) {
                paletteKinds.add(kind);
                continue;
            }

            String hay = (kind.title + " " + kind.name()).toLowerCase(Locale.ROOT);
            if (hay.contains(needle)) paletteKinds.add(kind);
        }

        paletteKinds.sort(Comparator
            .comparingInt((NodeKind kind) -> kind.isEvent() ? 0 : (kind.name().startsWith("If") ? 1 : 2))
            .thenComparing(kind -> kind.title, String.CASE_INSENSITIVE_ORDER));
    }

    private void drawPaletteScrollbar(DrawContext context, int rowHeight, int visibleRows, int maxScroll) {
        int trackW = 5;
        int trackX = paletteListX2 - trackW - 1;
        int trackY = paletteListY1 + 1;
        int trackH = Math.max(8, paletteListY2 - paletteListY1 - 2);

        context.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x5C1D2D3E);
        drawRectOutline(context, trackX, trackY, trackW, trackH, 0x8A3A5470);

        if (maxScroll <= 0) {
            int idleHandle = Math.max(14, rowHeight * 2);
            int handleY = trackY + (trackH - idleHandle) / 2;
            context.fill(trackX + 1, handleY, trackX + trackW - 1, handleY + idleHandle, 0x667D9DB8);
            return;
        }

        float viewportRatio = Math.max(0.08f, Math.min(1f, visibleRows / (float) (visibleRows + maxScroll)));
        int handleH = Math.max(14, (int) (trackH * viewportRatio));
        int travel = Math.max(1, trackH - handleH);
        int handleY = trackY + Math.round((paletteScroll / (float) maxScroll) * travel);

        context.fill(trackX + 1, handleY, trackX + trackW - 1, handleY + handleH, 0xD18FD3FF);
        context.fill(trackX + 1, handleY, trackX + trackW - 1, handleY + 3, 0xF0D2EBFF);
    }

    private void renderBottomPanel(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelTop = canvasBottom() + 6;

        context.fillGradient(8, panelTop, width - 8, height - 8, FlowGuiStyle.PANEL_TOP, FlowGuiStyle.PANEL_BOTTOM);
        drawRectOutline(context, 8, panelTop, width - 16, height - panelTop - 8, FlowGuiStyle.PANEL_BORDER);
        context.fill(width / 2, panelTop + 6, width / 2 + 1, height - 14, 0x3A5C7BA3);

        CustomFlowDefinition definition = module.getSelectedDefinition();
        FlowNode primary = primaryNode();
        FlowGraphComplexity.Result complexity = module.complexityOfSelectedDefinition();
        FlowRuntimeProfile runtimeProfile = module.runtimeProfileOfSelectedDefinition();

        int y = panelTop + 8;
        context.drawTextWithShadow(textRenderer, "Selected module: " + definition.name, 14, y, FlowGuiStyle.TEXT_PRIMARY);
        y += 12;

        context.drawTextWithShadow(
            textRenderer,
            "Selected nodes: " + selectedNodeIds.size()
                + " | Links: " + diagnostics.linkCount()
                + " | Issues: " + diagnostics.totalIssues()
                + " | Health: " + diagnostics.healthLabel(),
            14,
            y,
            FlowGuiStyle.TEXT_SECONDARY
        );
        y += 12;
        context.drawTextWithShadow(
            textRenderer,
            "Complexity: " + complexity.label()
                + " | Depth: " + complexity.maxDepth()
                + " | Branch: " + complexity.maxBranchWidth()
                + " | Runtime avg: " + String.format(Locale.ROOT, "%.2f", runtimeProfile.averageActionsPerTick()) + " act/tick",
            14,
            y,
            0xFFB7CDE5
        );
        y += 12;

        if (primary == null) {
            context.drawTextWithShadow(textRenderer, "Primary node: <none>", 14, y, FlowGuiStyle.TEXT_SECONDARY);
            y += 12;
        } else {
            context.drawTextWithShadow(textRenderer, "Primary: " + primary.kind.title + " (#" + primary.id + ")", 14, y, FlowGuiStyle.TEXT_PRIMARY);
            y += 12;

            if (primary.kind.supportsText()) {
                String mode = editingText ? "EDITING" : "IDLE";
                context.drawTextWithShadow(textRenderer, "Text(" + mode + "): " + trimForPanel(primary.text), 14, y, editingText ? 0xFF97F7B4 : 0xFFCAD8E9);
                y += 12;
            }

            if (primary.kind.supportsNumber()) {
                context.drawTextWithShadow(textRenderer, "Numeric value: " + primary.number, 14, y, 0xFFAED8FF);
                y += 12;
            }
        }

        if (linkingFromNodeId != null) {
            context.drawTextWithShadow(textRenderer, "Link mode from node #" + linkingFromNodeId + " (click an input port).", 14, y, 0xFF92F1B2);
            y += 12;
        }

        if (clipboard != null && !clipboard.nodes.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Clipboard: " + clipboard.nodes.size() + " nodes, " + clipboard.links.size() + " links.", 14, y, 0xFFAAC5E2);
            y += 12;
        }

        context.drawTextWithShadow(
            textRenderer,
            "Runtime: total " + runtimeProfile.totalActions()
                + " actions | tick max " + runtimeProfile.maxActionsInTick()
                + " | queue peak " + runtimeProfile.queuePeak()
                + " | watchdog " + runtimeProfile.watchdogTrips(),
            14,
            y,
            0xFFAAC5E2
        );
        y += 12;

        if (!transientStatusMessage.isBlank() && System.currentTimeMillis() <= transientStatusUntil) {
            context.drawTextWithShadow(textRenderer, transientStatusMessage, 14, y, 0xFF9AE8B5);
        }

        int rightX = width / 2 + 12;
        int controlsY = panelTop + 8;
        context.drawTextWithShadow(textRenderer, "Controls", rightX, controlsY, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "LMB node: select / Ctrl: toggle / drag selection", rightX, controlsY + 12, 0xFFC6D6E8);
        context.drawTextWithShadow(textRenderer, "LMB empty drag: box select | MMB drag: pan | Ctrl+Wheel: zoom", rightX, controlsY + 24, 0xFFC6D6E8);
        context.drawTextWithShadow(textRenderer, "LMB ports: connect | RMB node/link: delete", rightX, controlsY + 36, 0xFFC6D6E8);
        context.drawTextWithShadow(textRenderer, "Ctrl+Z/Y undo/redo | Ctrl+C/V copy/paste | Del remove", rightX, controlsY + 48, 0xFFC6D6E8);
        context.drawTextWithShadow(textRenderer, "T text edit | Up/Down/Wheel numeric | L link mode | C duplicate", rightX, controlsY + 60, 0xFFC6D6E8);

        int warnY = controlsY + 76;
        context.drawTextWithShadow(textRenderer, "Validation", rightX, warnY, FlowGuiStyle.TEXT_PRIMARY);
        if (validationMessages.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No blocking issues detected.", rightX, warnY + 12, 0xFF9CE1B2);
        } else {
            int warnColor = switch (diagnostics.healthLevel()) {
                case Healthy -> 0xFF9CE1B2;
                case Warning -> 0xFFFFD59B;
                case Critical -> 0xFFFFBE9F;
            };

            for (int i = 0; i < Math.min(3, validationMessages.size()); i++) {
                context.drawTextWithShadow(textRenderer, "- " + validationMessages.get(i), rightX, warnY + 12 + i * 12, warnColor);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        ensureToolbarLayout();
        if (handleToolbarClick(button, mouseX, mouseY)) return true;
        if (handlePaletteClick(button, mouseX, mouseY)) return true;

        if (!isInsideCanvas(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !controlKeyDown() && !shiftKeyDown()) {
                clearSelection();
                linkingFromNodeId = null;
                editingText = false;
            }
            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            beginPan(mouseX, mouseY);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (spaceKeyDown()) {
                beginPan(mouseX, mouseY);
                return true;
            }

            PortHit hit = findPortHit(mouseX, mouseY);
            if (hit != null) {
                if (hit.output()) {
                    linkingFromNodeId = hit.nodeId();
                    selectSingle(hit.nodeId());
                } else if (linkingFromNodeId != null && linkingFromNodeId != hit.nodeId()) {
                    if (!linkExists(linkingFromNodeId, hit.nodeId())) {
                        mutateGraph(() -> addLinkInternal(linkingFromNodeId, hit.nodeId()));
                        validationDirty = true;
                    }
                    linkingFromNodeId = null;
                    selectSingle(hit.nodeId());
                }
                return true;
            }

            FlowNode node = findNodeAt(mouseX, mouseY);
            if (node != null) {
                boolean control = controlKeyDown();
                boolean shift = shiftKeyDown();

                if (control) {
                    toggleSelection(node.id);
                } else if (shift) {
                    addSelection(node.id);
                } else if (!selectedNodeIds.contains(node.id) || selectedNodeIds.size() != 1) {
                    selectSingle(node.id);
                }

                beginNodeDrag(mouseX, mouseY);
                editingText = false;
                return true;
            }

            linkingFromNodeId = null;
            editingText = false;
            if (!controlKeyDown() && !shiftKeyDown()) clearSelection();
            beginBoxSelect(mouseX, mouseY, controlKeyDown() || shiftKeyDown());
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            FlowLink link = findLinkAt(mouseX, mouseY);
            if (link != null) {
                mutateGraph(() -> removeLinkInternal(link));
                return true;
            }

            FlowNode node = findNodeAt(mouseX, mouseY);
            if (node != null) {
                if (selectedNodeIds.contains(node.id) && selectedNodeIds.size() > 1) {
                    deleteSelectedNodes();
                } else {
                    mutateGraph(() -> removeNodeInternal(node.id));
                    clearSelection();
                }
                linkingFromNodeId = null;
                editingText = false;
                return true;
            }

            if (linkingFromNodeId != null) {
                linkingFromNodeId = null;
                return true;
            }
        }

        return false;
    }

    private boolean handleToolbarClick(int button, double mouseX, double mouseY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        for (ToolbarControl control : toolbarControls) {
            if (!isInside(mouseX, mouseY, control.x, control.y, control.w, control.h)) continue;
            control.action.run();
            return true;
        }

        return false;
    }

    private boolean handlePaletteClick(int button, double mouseX, double mouseY) {
        if (!isInsidePalettePanel(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) paletteFilterFocused = false;
            return false;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        if (isInside(mouseX, mouseY, paletteSearchX, paletteSearchY, paletteSearchW, paletteSearchH)) {
            paletteFilterFocused = true;
            paletteCursorIndex = paletteCursorIndexAtMouseX(mouseX);
            resetPaletteCursorBlink();
            return true;
        }

        paletteFilterFocused = false;
        for (PaletteRow row : paletteRows) {
            if (!isInside(mouseX, mouseY, row.x, row.y, row.w, row.h)) continue;
            spawnNode(row.kind);
            return true;
        }

        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (panning) {
            cameraX = panStartCameraX - (mouseX - panStartMouseX) / zoom;
            cameraY = panStartCameraY - (mouseY - panStartMouseY) / zoom;
            return;
        }

        if (draggingNodes) {
            double dx = (mouseX - dragStartMouseX) / zoom;
            double dy = (mouseY - dragStartMouseY) / zoom;

            if ((Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) && !dragMutationCaptured) {
                recordHistoryState();
                dragMutationCaptured = true;
            }

            for (Map.Entry<Integer, Vec2> entry : dragStartPositions.entrySet()) {
                FlowNode node = module.getNodeById(entry.getKey());
                if (node == null) continue;

                node.x = entry.getValue().x + dx;
                node.y = entry.getValue().y + dy;
            }

            validationDirty = true;
            return;
        }

        if (boxSelecting) {
            boxEndX = mouseX;
            boxEndY = mouseY;
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        boolean handled = false;

        if (panning && (click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            panning = false;
            handled = true;
        }

        if (draggingNodes && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingNodes = false;
            dragStartPositions.clear();

            if (dragMutationCaptured) {
                dragMutationCaptured = false;
                module.markGraphDirty();
                validationDirty = true;
            }

            handled = true;
        }

        if (boxSelecting && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boxSelecting = false;
            applyBoxSelection();
            handled = true;
        }

        if (handled) return true;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInside(mouseX, mouseY, paletteListX1, paletteListY1, paletteListX2 - paletteListX1, paletteListY2 - paletteListY1)) {
            if (verticalAmount > 0) paletteScroll = Math.max(0, paletteScroll - 1);
            if (verticalAmount < 0) paletteScroll++;
            return true;
        }

        if (isInsideCanvas(mouseX, mouseY)) {
            if (controlKeyDown()) {
                zoomAt(mouseX, mouseY, verticalAmount);
                return true;
            }

            FlowNode primary = primaryNode();
            if (primary != null && primary.kind.supportsNumber() && verticalAmount != 0) {
                int delta = verticalAmount > 0 ? 1 : -1;
                mutateGraph(() -> primary.number = Math.max(1, primary.number + delta));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        FlowNode primary = primaryNode();

        if (paletteFilterFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                paletteFilterFocused = false;
                return true;
            }

            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                deletePaletteBeforeCursor();
                return true;
            }

            if (key == GLFW.GLFW_KEY_DELETE) {
                deletePaletteAfterCursor();
                return true;
            }

            if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_V && client != null && client.keyboard != null) {
                String clip = client.keyboard.getClipboard();
                if (clip != null && !clip.isBlank()) {
                    insertPaletteFilter(clip);
                }
                return true;
            }

            if (key == GLFW.GLFW_KEY_LEFT) {
                movePaletteCursorByCodePoints(-1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_RIGHT) {
                movePaletteCursorByCodePoints(1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_HOME) {
                paletteCursorIndex = 0;
                resetPaletteCursorBlink();
                return true;
            }

            if (key == GLFW.GLFW_KEY_END) {
                paletteCursorIndex = paletteFilter.length();
                resetPaletteCursorBlink();
                return true;
            }

            return true;
        }

        if (primary != null && editingText && primary.kind.supportsText()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                editingText = false;
                if (textEditMutated) {
                    module.markGraphDirty();
                    validationDirty = true;
                }
                textEditHistoryCaptured = false;
                textEditMutated = false;
                return true;
            }

            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (primary.text != null && !primary.text.isEmpty()) {
                    ensureTextEditHistoryCaptured();
                    int end = primary.text.offsetByCodePoints(primary.text.length(), -1);
                    primary.text = primary.text.substring(0, end);
                    textEditMutated = true;
                    validationDirty = true;
                }
                return true;
            }

            if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_V && client != null && client.keyboard != null) {
                String clip = client.keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    ensureTextEditHistoryCaptured();
                    if (primary.text == null) primary.text = "";
                    primary.text += clip;
                    textEditMutated = true;
                    validationDirty = true;
                }
                return true;
            }

            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_S) {
            module.markGraphDirty();
            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_C) {
            copySelection();
            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_V) {
            pasteClipboard();
            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_A) {
            selectAllNodes();
            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_F) {
            paletteFilterFocused = true;
            paletteCursorIndex = paletteFilter.length();
            resetPaletteCursorBlink();
            return true;
        }

        if (super.keyPressed(input)) return true;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (linkingFromNodeId != null) {
                linkingFromNodeId = null;
                return true;
            }

            if (paletteFilterFocused) {
                paletteFilterFocused = false;
                return true;
            }

            close();
            return true;
        }

        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            module.selectPreviousDefinition();
            onDefinitionChanged();
            return true;
        }

        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            module.selectNextDefinition();
            onDefinitionChanged();
            return true;
        }

        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!selectedNodeIds.isEmpty()) {
                deleteSelectedNodes();
                return true;
            }
        }

        if (primary == null) return false;

        switch (key) {
            case GLFW.GLFW_KEY_T -> {
                if (primary.kind.supportsText()) {
                    editingText = true;
                    textEditHistoryCaptured = false;
                    textEditMutated = false;
                    if (primary.text == null) primary.text = "";
                    return true;
                }
            }
            case GLFW.GLFW_KEY_UP -> {
                if (primary.kind.supportsNumber()) {
                    mutateGraph(() -> primary.number++);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (primary.kind.supportsNumber()) {
                    mutateGraph(() -> primary.number = Math.max(1, primary.number - 1));
                    return true;
                }
            }
            case GLFW.GLFW_KEY_L -> {
                linkingFromNodeId = primary.id;
                return true;
            }
            case GLFW.GLFW_KEY_C -> {
                duplicateSelection();
                return true;
            }
            case GLFW.GLFW_KEY_G -> {
                snapSelectionToGrid();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        int cp = input.codepoint();
        if (Character.isISOControl(cp)) return true;

        if (paletteFilterFocused) {
            insertPaletteFilter(input.asString());
            return true;
        }

        if (!editingText) return super.charTyped(input);

        FlowNode primary = primaryNode();
        if (primary == null || !primary.kind.supportsText()) return true;

        if (primary.text == null) primary.text = "";
        if (primary.text.length() >= 300) return true;

        ensureTextEditHistoryCaptured();
        primary.text += input.asString();
        textEditMutated = true;
        validationDirty = true;
        return true;
    }

    @Override
    public void close() {
        if (textEditMutated) module.markGraphDirty();
        module.markGraphDirty();
        super.close();
    }

    private void insertPaletteFilter(String value) {
        if (value == null || value.isEmpty()) return;

        String cleaned = value.replace('\n', ' ').replace('\r', ' ');
        int remaining = 48 - paletteFilter.codePointCount(0, paletteFilter.length());
        if (remaining <= 0) return;

        String clipped = trimToCodePoints(cleaned, remaining);
        if (clipped.isEmpty()) return;

        clampPaletteCursor();
        paletteFilter = paletteFilter.substring(0, paletteCursorIndex) + clipped + paletteFilter.substring(paletteCursorIndex);
        paletteCursorIndex += clipped.length();
        paletteScroll = 0;
        resetPaletteCursorBlink();
    }

    private void deletePaletteBeforeCursor() {
        clampPaletteCursor();
        if (paletteCursorIndex <= 0 || paletteFilter.isEmpty()) {
            resetPaletteCursorBlink();
            return;
        }

        int start = paletteFilter.offsetByCodePoints(paletteCursorIndex, -1);
        paletteFilter = paletteFilter.substring(0, start) + paletteFilter.substring(paletteCursorIndex);
        paletteCursorIndex = start;
        paletteScroll = 0;
        resetPaletteCursorBlink();
    }

    private void deletePaletteAfterCursor() {
        clampPaletteCursor();
        if (paletteCursorIndex >= paletteFilter.length() || paletteFilter.isEmpty()) {
            resetPaletteCursorBlink();
            return;
        }

        int end = paletteFilter.offsetByCodePoints(paletteCursorIndex, 1);
        paletteFilter = paletteFilter.substring(0, paletteCursorIndex) + paletteFilter.substring(end);
        paletteScroll = 0;
        resetPaletteCursorBlink();
    }

    private void movePaletteCursorByCodePoints(int delta) {
        if (delta == 0) return;

        clampPaletteCursor();
        if (delta > 0 && paletteCursorIndex < paletteFilter.length()) {
            paletteCursorIndex = paletteFilter.offsetByCodePoints(paletteCursorIndex, 1);
        } else if (delta < 0 && paletteCursorIndex > 0) {
            paletteCursorIndex = paletteFilter.offsetByCodePoints(paletteCursorIndex, -1);
        }

        resetPaletteCursorBlink();
    }

    private int paletteCursorIndexAtMouseX(double mouseX) {
        if (paletteFilter.isEmpty()) return 0;

        int relative = (int) Math.round(mouseX - (paletteSearchX + 5));
        if (relative <= 0) return 0;

        int previousIndex = 0;
        int previousWidth = 0;
        int index = 0;
        while (index < paletteFilter.length()) {
            int next = paletteFilter.offsetByCodePoints(index, 1);
            int nextWidth = textRenderer.getWidth(paletteFilter.substring(0, next));
            if (relative <= nextWidth) {
                int distPrev = Math.abs(relative - previousWidth);
                int distNext = Math.abs(nextWidth - relative);
                return distPrev <= distNext ? previousIndex : next;
            }

            previousIndex = next;
            previousWidth = nextWidth;
            index = next;
        }

        return paletteFilter.length();
    }

    private void clampPaletteCursor() {
        paletteCursorIndex = MathHelper.clamp(paletteCursorIndex, 0, paletteFilter.length());
    }

    private void resetPaletteCursorBlink() {
        paletteCursorBlinkStartedAt = System.currentTimeMillis();
    }

    private static String trimToCodePoints(String value, int maxCodePoints) {
        if (value == null || value.isEmpty() || maxCodePoints <= 0) return "";
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) return value;

        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }

    private void ensureTextEditHistoryCaptured() {
        if (textEditHistoryCaptured) return;
        recordHistoryState();
        textEditHistoryCaptured = true;
    }

    private void toggleModuleAvailability() {
        CustomFlowDefinition definition = module.getSelectedDefinition();
        module.toggleDefinitionAvailable(definition.id);
    }

    private void toggleModuleRunning() {
        CustomFlowDefinition definition = module.getSelectedDefinition();
        module.toggleDefinitionRunning(definition.id);
    }

    private void panicStopAll() {
        int stopped = module.panicStopAllDefinitions();
        if (stopped <= 0) {
            showTransientStatus("Panic stop: no running modules.");
            return;
        }

        showTransientStatus("Panic stop: stopped " + stopped + " module(s).");
    }

    private void resetAllRuntimeStats() {
        int reset = module.resetAllRuntimeProfiles();
        if (reset <= 0) {
            showTransientStatus("Runtime stats reset: no profiles available.");
            return;
        }

        showTransientStatus("Runtime stats reset for " + reset + " profile(s).");
    }

    private void autoCleanupGraph() {
        FlowGraphCleanup.Report report = module.cleanupSelectedGraph();
        validationDirty = true;
        showTransientStatus(report.changed() ? ("Cleanup completed: " + report.summary()) : "Cleanup found no changes.");
    }

    private void exportPresetToClipboard() {
        FlowPresetCodec.ExportResult exported = module.exportSelectedPreset();
        if (!exported.success()) {
            showTransientStatus("Preset export failed: " + exported.message());
            return;
        }

        if (!module.copySelectedPresetToClipboard()) {
            showTransientStatus("Preset export failed: clipboard unavailable.");
            return;
        }

        showTransientStatus("Preset copied to clipboard (" + (exported.signed() ? "signed" : "unsigned") + ", checksum " + trimForPanel(exported.checksum()) + ").");
    }

    private void importPresetFromClipboard() {
        FlowForgeModule.PresetImportResult imported = module.importPresetFromClipboard(false);
        if (!imported.success()) {
            showTransientStatus("Preset import failed: " + imported.message());
            return;
        }

        onDefinitionChanged();
        showTransientStatus("Preset imported into module #" + imported.definitionId() + " (" + (imported.signatureVerified() ? "signature verified" : (imported.signed() ? "signature unverified" : "unsigned")) + ").");
    }

    private void clearGraph() {
        if (module.nodes().isEmpty() && module.links().isEmpty()) return;

        mutateGraph(() -> {
            CustomFlowDefinition definition = module.getSelectedDefinition();
            definition.graph.nodes.clear();
            definition.graph.links.clear();
            definition.graph.nextNodeId = 1;
            clearSelection();
            linkingFromNodeId = null;
            editingText = false;
        });
    }

    private void resetView() {
        cameraX = canvasCenterX();
        cameraY = canvasCenterY();
        zoom = 1.0;
    }

    private void showTransientStatus(String message) {
        transientStatusMessage = message == null ? "" : message;
        transientStatusUntil = System.currentTimeMillis() + 4200L;
    }

    private void spawnNode(NodeKind kind) {
        int index = module.nodes().size();
        double x = cameraX - NODE_WIDTH / 2.0 + (index % 4) * 18;
        double y = cameraY - NODE_HEIGHT / 2.0 + ((index / 4) % 4) * 16;

        mutateGraph(() -> {
            FlowNode node = addNodeInternal(kind, x, y);
            selectSingle(node.id);
            linkingFromNodeId = null;
            editingText = false;
        });
    }

    private void beginNodeDrag(double mouseX, double mouseY) {
        draggingNodes = true;
        dragMutationCaptured = false;
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;

        dragStartPositions.clear();
        for (int nodeId : selectedNodeIds) {
            FlowNode node = module.getNodeById(nodeId);
            if (node == null) continue;
            dragStartPositions.put(nodeId, new Vec2(node.x, node.y));
        }
    }

    private void beginBoxSelect(double mouseX, double mouseY, boolean additive) {
        boxSelecting = true;
        boxSelectAdditive = additive;
        boxStartX = mouseX;
        boxStartY = mouseY;
        boxEndX = mouseX;
        boxEndY = mouseY;
    }

    private void beginPan(double mouseX, double mouseY) {
        panning = true;
        panStartMouseX = mouseX;
        panStartMouseY = mouseY;
        panStartCameraX = cameraX;
        panStartCameraY = cameraY;
    }

    private void applyBoxSelection() {
        double minX = Math.min(boxStartX, boxEndX);
        double minY = Math.min(boxStartY, boxEndY);
        double maxX = Math.max(boxStartX, boxEndX);
        double maxY = Math.max(boxStartY, boxEndY);

        if (!boxSelectAdditive) clearSelection();

        for (FlowNode node : module.nodes()) {
            NodeRect rect = rectForNode(node);
            if (rect.x > maxX || rect.y > maxY || rect.x + rect.w < minX || rect.y + rect.h < minY) continue;
            addSelection(node.id);
        }

        sanitizeSelection();
    }

    private void selectAllNodes() {
        selectedNodeIds.clear();
        for (FlowNode node : module.nodes()) {
            selectedNodeIds.add(node.id);
        }

        if (!selectedNodeIds.isEmpty()) {
            primarySelectedNodeId = selectedNodeIds.iterator().next();
        }
    }

    private void clearSelection() {
        selectedNodeIds.clear();
        primarySelectedNodeId = -1;
    }

    private void selectSingle(int nodeId) {
        clearSelection();
        if (nodeId == -1) return;

        selectedNodeIds.add(nodeId);
        primarySelectedNodeId = nodeId;
    }

    private void addSelection(int nodeId) {
        if (nodeId == -1) return;

        selectedNodeIds.add(nodeId);
        primarySelectedNodeId = nodeId;
    }

    private void toggleSelection(int nodeId) {
        if (selectedNodeIds.contains(nodeId)) {
            selectedNodeIds.remove(nodeId);
            if (primarySelectedNodeId == nodeId) {
                primarySelectedNodeId = selectedNodeIds.isEmpty() ? -1 : selectedNodeIds.iterator().next();
            }
        } else {
            selectedNodeIds.add(nodeId);
            primarySelectedNodeId = nodeId;
        }
    }

    private void sanitizeSelection() {
        selectedNodeIds.removeIf(nodeId -> module.getNodeById(nodeId) == null);

        if (selectedNodeIds.isEmpty()) {
            primarySelectedNodeId = -1;
            return;
        }

        if (!selectedNodeIds.contains(primarySelectedNodeId)) {
            primarySelectedNodeId = selectedNodeIds.iterator().next();
        }
    }

    private FlowNode primaryNode() {
        sanitizeSelection();
        if (primarySelectedNodeId == -1) return null;
        return module.getNodeById(primarySelectedNodeId);
    }

    private void deleteSelectedNodes() {
        if (selectedNodeIds.isEmpty()) return;

        List<Integer> ids = new ArrayList<>(selectedNodeIds);
        mutateGraph(() -> {
            for (int id : ids) {
                removeNodeInternal(id);
            }

            clearSelection();
            linkingFromNodeId = null;
            editingText = false;
        });
    }

    private void snapSelectionToGrid() {
        if (selectedNodeIds.isEmpty()) return;

        mutateGraph(() -> {
            for (int id : selectedNodeIds) {
                FlowNode node = module.getNodeById(id);
                if (node == null) continue;

                node.x = Math.round(node.x / GRID_SIZE) * GRID_SIZE;
                node.y = Math.round(node.y / GRID_SIZE) * GRID_SIZE;
            }
        });
    }

    private void copySelection() {
        sanitizeSelection();
        if (selectedNodeIds.isEmpty()) return;

        List<FlowNode> ordered = selectedNodesInVisualOrder();
        if (ordered.isEmpty()) return;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;

        for (FlowNode node : ordered) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
        }

        List<ClipboardNode> clipNodes = new ArrayList<>();
        for (FlowNode node : ordered) {
            clipNodes.add(new ClipboardNode(
                node.id,
                node.kind,
                node.x - minX,
                node.y - minY,
                node.text == null ? "" : node.text,
                Math.max(1, node.number)
            ));
        }

        List<ClipboardLink> clipLinks = new ArrayList<>();
        for (FlowLink link : module.links()) {
            if (!selectedNodeIds.contains(link.fromNodeId) || !selectedNodeIds.contains(link.toNodeId)) continue;
            clipLinks.add(new ClipboardLink(link.fromNodeId, link.toNodeId));
        }

        clipboard = new ClipboardGraph(clipNodes, clipLinks);
    }

    private void duplicateSelection() {
        sanitizeSelection();
        if (selectedNodeIds.isEmpty()) return;

        List<FlowNode> ordered = selectedNodesInVisualOrder();
        if (ordered.isEmpty()) return;
        List<FlowLink> sourceLinks = new ArrayList<>(module.links());

        mutateGraph(() -> {
            Map<Integer, Integer> idMap = new HashMap<>();
            clearSelection();

            for (FlowNode source : ordered) {
                FlowNode duplicate = addNodeInternal(source.kind, source.x + 18, source.y + 18);
                duplicate.text = source.text == null ? "" : source.text;
                duplicate.number = Math.max(1, source.number);

                idMap.put(source.id, duplicate.id);
                addSelection(duplicate.id);
            }

            for (FlowLink link : sourceLinks) {
                Integer from = idMap.get(link.fromNodeId);
                Integer to = idMap.get(link.toNodeId);
                if (from == null || to == null) continue;
                addLinkInternal(from, to);
            }

            linkingFromNodeId = null;
            editingText = false;
        });
    }

    private void pasteClipboard() {
        if (clipboard == null || clipboard.nodes.isEmpty()) return;

        double baseX;
        double baseY;
        if (isInsideCanvas(lastMouseX, lastMouseY)) {
            baseX = screenToWorldX(lastMouseX);
            baseY = screenToWorldY(lastMouseY);
        } else {
            baseX = cameraX;
            baseY = cameraY;
        }

        mutateGraph(() -> {
            Map<Integer, Integer> idMap = new HashMap<>();
            clearSelection();

            for (ClipboardNode source : clipboard.nodes) {
                FlowNode node = addNodeInternal(source.kind, baseX + source.offsetX, baseY + source.offsetY);
                node.text = source.text;
                node.number = Math.max(1, source.number);

                idMap.put(source.originalId, node.id);
                addSelection(node.id);
            }

            for (ClipboardLink link : clipboard.links) {
                Integer from = idMap.get(link.fromNodeId);
                Integer to = idMap.get(link.toNodeId);
                if (from == null || to == null) continue;
                addLinkInternal(from, to);
            }

            linkingFromNodeId = null;
            editingText = false;
        });
    }

    private List<FlowNode> selectedNodesInVisualOrder() {
        List<FlowNode> ordered = new ArrayList<>();
        for (FlowNode node : module.nodes()) {
            if (!selectedNodeIds.contains(node.id)) continue;
            ordered.add(node);
        }

        return ordered;
    }

    private void ensureHistoryBoundToCurrentDefinition() {
        int definitionId = module.getSelectedDefinition().id;
        if (historyDefinitionId == definitionId) return;

        historyDefinitionId = definitionId;
        undoStack.clear();
        redoStack.clear();
        clearSelection();
        linkingFromNodeId = null;
        editingText = false;
        textEditHistoryCaptured = false;
        textEditMutated = false;
        validationDirty = true;
    }

    private void recordHistoryState() {
        undoStack.push(captureGraphState());
        while (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private GraphHistoryState captureGraphState() {
        CustomFlowDefinition definition = module.getSelectedDefinition();

        List<FlowNode> nodesCopy = new ArrayList<>(definition.graph.nodes.size());
        for (FlowNode node : definition.graph.nodes) {
            nodesCopy.add(copyNode(node));
        }

        List<FlowLink> linksCopy = new ArrayList<>(definition.graph.links.size());
        for (FlowLink link : definition.graph.links) {
            linksCopy.add(new FlowLink(link.fromNodeId, link.toNodeId));
        }

        return new GraphHistoryState(
            nodesCopy,
            linksCopy,
            definition.graph.nextNodeId,
            new LinkedHashSet<>(selectedNodeIds),
            primarySelectedNodeId,
            cameraX,
            cameraY,
            zoom
        );
    }

    private void applyGraphState(GraphHistoryState state) {
        CustomFlowDefinition definition = module.getSelectedDefinition();

        definition.graph.nodes.clear();
        for (FlowNode node : state.nodes) {
            definition.graph.nodes.add(copyNode(node));
        }

        definition.graph.links.clear();
        for (FlowLink link : state.links) {
            definition.graph.links.add(new FlowLink(link.fromNodeId, link.toNodeId));
        }

        definition.graph.nextNodeId = Math.max(1, state.nextNodeId);

        selectedNodeIds.clear();
        selectedNodeIds.addAll(state.selectedIds);
        primarySelectedNodeId = state.primaryId;
        sanitizeSelection();

        cameraX = state.cameraX;
        cameraY = state.cameraY;
        zoom = MathHelper.clamp(state.zoom, 0.35, 2.8);

        linkingFromNodeId = null;
        editingText = false;
        textEditHistoryCaptured = false;
        textEditMutated = false;

        module.markGraphDirty();
        validationDirty = true;
    }

    private void undo() {
        ensureHistoryBoundToCurrentDefinition();
        if (undoStack.isEmpty()) return;

        redoStack.push(captureGraphState());
        GraphHistoryState previous = undoStack.pop();
        applyGraphState(previous);
    }

    private void redo() {
        ensureHistoryBoundToCurrentDefinition();
        if (redoStack.isEmpty()) return;

        undoStack.push(captureGraphState());
        GraphHistoryState next = redoStack.pop();
        applyGraphState(next);
    }

    private void mutateGraph(Runnable mutation) {
        recordHistoryState();
        mutation.run();
        sanitizeSelection();
        module.markGraphDirty();
        validationDirty = true;
    }

    private FlowNode addNodeInternal(NodeKind kind, double x, double y) {
        CustomFlowDefinition definition = module.getSelectedDefinition();

        FlowNode node = FlowNode.create(definition.graph.nextNodeId++, kind, x, y);
        definition.graph.nodes.add(node);
        return node;
    }

    private boolean addLinkInternal(int fromNodeId, int toNodeId) {
        if (fromNodeId == toNodeId) return false;
        if (module.getNodeById(fromNodeId) == null || module.getNodeById(toNodeId) == null) return false;

        if (linkExists(fromNodeId, toNodeId)) return false;

        module.getSelectedDefinition().graph.links.add(new FlowLink(fromNodeId, toNodeId));
        return true;
    }

    private void removeLinkInternal(FlowLink link) {
        if (link == null) return;
        module.getSelectedDefinition().graph.links.remove(link);
    }

    private void removeNodeInternal(int nodeId) {
        CustomFlowDefinition definition = module.getSelectedDefinition();
        definition.graph.nodes.removeIf(node -> node.id == nodeId);
        definition.graph.links.removeIf(link -> link.fromNodeId == nodeId || link.toNodeId == nodeId);
    }

    private boolean linkExists(int fromNodeId, int toNodeId) {
        for (FlowLink link : module.links()) {
            if (link.fromNodeId == fromNodeId && link.toNodeId == toNodeId) return true;
        }

        return false;
    }

    private void zoomAt(double mouseX, double mouseY, double wheelDelta) {
        if (wheelDelta == 0) return;

        double beforeX = screenToWorldX(mouseX);
        double beforeY = screenToWorldY(mouseY);

        double factor = wheelDelta > 0 ? 1.11 : 0.9;
        zoom = MathHelper.clamp(zoom * factor, 0.35, 2.8);

        double afterX = screenToWorldX(mouseX);
        double afterY = screenToWorldY(mouseY);

        cameraX += beforeX - afterX;
        cameraY += beforeY - afterY;
    }

    private void refreshValidationIfNeeded() {
        if (!validationDirty) return;

        diagnostics = FlowGraphDiagnostics.analyze(module.getSelectedDefinition());

        warningNodeIds.clear();
        warningNodeIds.addAll(diagnostics.issueNodeIds());

        validationMessages.clear();
        validationMessages.addAll(diagnostics.messages());

        validationDirty = false;
    }

    private void pruneHoverState() {
        Set<Integer> validNodeIds = new HashSet<>();
        for (FlowNode node : module.nodes()) {
            validNodeIds.add(node.id);
        }
        nodeHoverProgress.keySet().retainAll(validNodeIds);

        Set<NodeKind> validKinds = new HashSet<>();
        for (NodeKind kind : NodeKind.values()) {
            validKinds.add(kind);
        }
        paletteRowHoverProgress.keySet().retainAll(validKinds);

        Set<String> validToolbarIds = new HashSet<>();
        for (ToolbarControl control : toolbarControls) {
            validToolbarIds.add(control.id);
        }
        toolbarHoverProgress.keySet().retainAll(validToolbarIds);
    }

    private <K> float animateHover(Map<K, Float> stateMap, K key, boolean active, float delta, float responsiveness) {
        float current = stateMap.getOrDefault(key, 0f);
        float next = FlowGuiStyle.animate(current, active, delta, responsiveness);
        stateMap.put(key, next);
        return next;
    }

    private boolean shouldBlinkCursor() {
        long elapsed = Math.max(0, System.currentTimeMillis() - paletteCursorBlinkStartedAt);
        return (elapsed / 450L) % 2L == 0L;
    }

    private String formatZoom() {
        return String.format(Locale.ROOT, "%.2fx", zoom);
    }

    private String trimForNode(String value, int maxWidth) {
        String trimmed = textRenderer.trimToWidth(value, Math.max(16, maxWidth));
        if (trimmed.length() < value.length() && trimmed.length() > 3) {
            return trimmed.substring(0, trimmed.length() - 3) + "...";
        }

        return trimmed;
    }

    private String trimForPanel(String value) {
        if (value == null || value.isEmpty()) return "<empty>";

        int maxWidth = Math.max(80, width / 2 - 36);
        String trimmed = textRenderer.trimToWidth(value, maxWidth);
        if (trimmed.length() < value.length() && trimmed.length() > 3) {
            return trimmed.substring(0, trimmed.length() - 3) + "...";
        }

        return trimmed;
    }

    private boolean isInsideCanvas(double x, double y) {
        return x >= canvasLeft() && x <= canvasRight() && y >= canvasTop() && y <= canvasBottom();
    }

    private boolean isInsidePalettePanel(double x, double y) {
        return x >= palettePanelX1() && x <= palettePanelX2() && y >= canvasTop() && y <= canvasBottom();
    }

    private FlowNode findNodeAt(double mouseX, double mouseY) {
        List<FlowNode> nodes = module.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            FlowNode node = nodes.get(i);
            NodeRect rect = rectForNode(node);

            if (isInside(mouseX, mouseY, rect.x, rect.y, rect.w, rect.h)) {
                return node;
            }
        }

        return null;
    }

    private PortHit findPortHit(double mouseX, double mouseY) {
        List<FlowNode> nodes = module.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            FlowNode node = nodes.get(i);
            NodeRect rect = rectForNode(node);

            int py = portTopY(rect);
            int inX = inputPortX(rect);
            int outX = outputPortX(rect);
            int port = scaledPortSize();

            if (isInside(mouseX, mouseY, outX, py, port, port)) {
                return new PortHit(node.id, true);
            }

            if (isInside(mouseX, mouseY, inX, py, port, port)) {
                return new PortHit(node.id, false);
            }
        }

        return null;
    }

    private FlowLink findLinkAt(double mouseX, double mouseY) {
        for (FlowLink link : module.links()) {
            FlowNode from = module.getNodeById(link.fromNodeId);
            FlowNode to = module.getNodeById(link.toNodeId);
            if (from == null || to == null) continue;

            NodeRect fromRect = rectForNode(from);
            NodeRect toRect = rectForNode(to);

            double distance = distanceToSegment(mouseX, mouseY, outputCenterX(fromRect), portCenterY(fromRect), inputCenterX(toRect), portCenterY(toRect));
            if (distance <= Math.max(5.0, zoom * 5.0)) {
                return link;
            }
        }

        return null;
    }

    private int canvasLeft() {
        return 8;
    }

    private int canvasTop() {
        return toolbarBottom + 8;
    }

    private int inspectorWidth() {
        return Math.min(300, Math.max(220, width / 4));
    }

    private int canvasRight() {
        return Math.max(canvasLeft() + 220, width - inspectorWidth() - 14);
    }

    private int canvasBottom() {
        return Math.max(canvasTop() + NODE_HEIGHT + 24, height - BOTTOM_PANEL_HEIGHT - 8);
    }

    private int palettePanelX1() {
        return canvasRight() + 6;
    }

    private int palettePanelX2() {
        return width - 8;
    }

    private double canvasCenterX() {
        return (canvasLeft() + canvasRight()) * 0.5;
    }

    private double canvasCenterY() {
        return (canvasTop() + canvasBottom()) * 0.5;
    }

    private int scaledPortSize() {
        return Math.max(6, scale(PORT_SIZE));
    }

    private int scale(int value) {
        return Math.max(1, (int) Math.round(value * zoom));
    }

    private double worldToScreenX(double worldX) {
        return (worldX - cameraX) * zoom + canvasCenterX();
    }

    private double worldToScreenY(double worldY) {
        return (worldY - cameraY) * zoom + canvasCenterY();
    }

    private double screenToWorldX(double screenX) {
        return (screenX - canvasCenterX()) / zoom + cameraX;
    }

    private double screenToWorldY(double screenY) {
        return (screenY - canvasCenterY()) / zoom + cameraY;
    }

    private NodeRect rectForNode(FlowNode node) {
        int x = (int) Math.round(worldToScreenX(node.x));
        int y = (int) Math.round(worldToScreenY(node.y));

        int w = Math.max(76, scale(NODE_WIDTH));
        int h = Math.max(38, scale(NODE_HEIGHT));

        return new NodeRect(x, y, w, h);
    }

    private int inputPortX(NodeRect rect) {
        return rect.x - scaledPortSize();
    }

    private int outputPortX(NodeRect rect) {
        return rect.x + rect.w;
    }

    private int portTopY(NodeRect rect) {
        return rect.y + rect.h / 2 - scaledPortSize() / 2;
    }

    private int inputCenterX(NodeRect rect) {
        return inputPortX(rect) + scaledPortSize() / 2;
    }

    private int outputCenterX(NodeRect rect) {
        return outputPortX(rect) + scaledPortSize() / 2;
    }

    private int portCenterY(NodeRect rect) {
        return portTopY(rect) + scaledPortSize() / 2;
    }

    private void drawRectOutline(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;

        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color, int thickness) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));

        if (steps == 0) {
            context.fill(x1, y1, x1 + thickness, y1 + thickness, color);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            int h = Math.max(1, thickness / 2);
            context.fill(x - h, y - h, x + h + 1, y + h + 1, color);
        }
    }

    private void drawCable(DrawContext context, int x1, int y1, int x2, int y2, int color, int glowColor) {
        int segments = 22;
        double dx = Math.abs(x2 - x1);
        double c1x = x1 + Math.max(24, dx * 0.35);
        double c2x = x2 - Math.max(24, dx * 0.35);
        double c1y = y1;
        double c2y = y2;

        int glowThickness = Math.max(3, (int) Math.round(3 * zoom));
        int lineThickness = Math.max(2, (int) Math.round(2 * zoom));

        double prevX = x1;
        double prevY = y1;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double omt = 1 - t;
            double x = omt * omt * omt * x1 + 3 * omt * omt * t * c1x + 3 * omt * t * t * c2x + t * t * t * x2;
            double y = omt * omt * omt * y1 + 3 * omt * omt * t * c1y + 3 * omt * t * t * c2y + t * t * t * y2;

            drawLine(context, (int) prevX, (int) prevY, (int) x, (int) y, glowColor, glowThickness);
            drawLine(context, (int) prevX, (int) prevY, (int) x, (int) y, color, lineThickness);

            prevX = x;
            prevY = y;
        }
    }

    private void drawGrid(DrawContext context, int left, int top, int right, int bottom) {
        double worldLeft = screenToWorldX(left);
        double worldRight = screenToWorldX(right);
        double worldTop = screenToWorldY(top);
        double worldBottom = screenToWorldY(bottom);

        double startX = Math.floor(worldLeft / GRID_SIZE) * GRID_SIZE;
        double startY = Math.floor(worldTop / GRID_SIZE) * GRID_SIZE;

        for (double wx = startX; wx <= worldRight; wx += GRID_SIZE) {
            int x = (int) Math.round(worldToScreenX(wx));
            int alpha = (((int) Math.floor(wx / GRID_SIZE)) & 1) == 0 ? 0x12 : 0x1A;
            context.fill(x, top, x + 1, bottom, (alpha << 24) | 0x6B8AA7);
        }

        for (double wy = startY; wy <= worldBottom; wy += GRID_SIZE) {
            int y = (int) Math.round(worldToScreenY(wy));
            int alpha = (((int) Math.floor(wy / GRID_SIZE)) & 1) == 0 ? 0x12 : 0x1A;
            context.fill(left, y, right, y + 1, (alpha << 24) | 0x6B8AA7);
        }
    }

    private static int blendColor(int base, int target, float factor) {
        return FlowGuiStyle.blend(base, target, factor);
    }

    private static int brighten(int color, float amount) {
        return FlowGuiStyle.brighten(color, amount);
    }

    private static boolean isInside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    private static boolean isControlDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
    }

    private boolean controlKeyDown() {
        if (client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;
    }

    private boolean shiftKeyDown() {
        if (client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean spaceKeyDown() {
        if (client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }

    private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;

        if (dx == 0 && dy == 0) {
            return Math.hypot(px - x1, py - y1);
        }

        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = MathHelper.clamp(t, 0.0, 1.0);

        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        return Math.hypot(px - closestX, py - closestY);
    }

    private static FlowNode copyNode(FlowNode source) {
        FlowNode copy = new FlowNode();
        copy.id = source.id;
        copy.kind = source.kind;
        copy.x = source.x;
        copy.y = source.y;
        copy.text = source.text == null ? "" : source.text;
        copy.number = Math.max(1, source.number);
        return copy;
    }

    private record PortHit(int nodeId, boolean output) {
    }

    private record NodeRect(int x, int y, int w, int h) {
    }

    private record Vec2(double x, double y) {
    }

    private record GraphHistoryState(
        List<FlowNode> nodes,
        List<FlowLink> links,
        int nextNodeId,
        Set<Integer> selectedIds,
        int primaryId,
        double cameraX,
        double cameraY,
        double zoom
    ) {
    }

    private record ClipboardGraph(List<ClipboardNode> nodes, List<ClipboardLink> links) {
    }

    private record ClipboardNode(int originalId, NodeKind kind, double offsetX, double offsetY, String text, int number) {
    }

    private record ClipboardLink(int fromNodeId, int toNodeId) {
    }

    private record PaletteRow(NodeKind kind, int x, int y, int w, int h) {
    }

    private record ToolbarControl(String id, String label, int x, int y, int w, int h, int baseColor, int textColor, Runnable action) {
    }
}
