package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.CustomFlowDefinition;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FlowModuleManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 36;

    private static final int STATUS_W = 92;
    private static final int STATUS_H = 18;

    private static final int ACTION_W = 74;
    private static final int ACTION_H = 18;
    private static final int ACTION_GAP = 6;

    private static final int TOOLBAR_BUTTON_H = 18;
    private static final int SEARCH_H = 18;
    private static final int SEARCH_MAX_LEN = 64;
    private static final int RENAME_MAX_LEN = 64;
    private static final int TABLE_SCROLLBAR_W = 6;

    private final FlowForgeModule forge;

    private final List<CustomFlowDefinition> filtered = new ArrayList<>();
    private final Set<Integer> selectedIds = new LinkedHashSet<>();
    private final List<ControlButton> controls = new ArrayList<>();
    private final Map<Integer, FlowGraphDiagnostics.Result> diagnosticsByDefinitionId = new HashMap<>();
    private final Map<Integer, FlowGraphComplexity.Result> complexityByDefinitionId = new HashMap<>();
    private final Map<String, Float> controlHoverProgress = new HashMap<>();
    private final Map<Integer, Float> rowHoverProgress = new HashMap<>();
    private final Map<String, Float> actionHoverProgress = new HashMap<>();
    private final Map<String, Float> badgeHoverProgress = new HashMap<>();
    private final Map<String, Float> modalHoverProgress = new HashMap<>();

    private float searchHoverProgress;

    private int scroll;
    private int anchorSelectionId = -1;

    private String searchQuery = "";
    private boolean searchFocused;
    private long searchFocusStartedAt;
    private int searchCursorIndex;

    private VisibilityFilter visibilityFilter = VisibilityFilter.All;
    private RuntimeFilter runtimeFilter = RuntimeFilter.All;
    private SortMode sortMode = SortMode.NameAsc;

    private boolean confirmDeleteVisible;
    private final List<Integer> pendingDeleteIds = new ArrayList<>();
    private boolean renameModalVisible;
    private int renameTargetId = -1;
    private String renameValue = "";
    private int renameCursorIndex;
    private long renameFocusStartedAt;
    private boolean renameInputFocused;

    private int searchX;
    private int searchY;
    private int searchW;
    private int searchH;

    private int tableX1;
    private int tableY1;
    private int tableX2;
    private int tableY2;

    public FlowModuleManagerScreen(FlowForgeModule forge) {
        super(Text.literal("Flow Module Manager"));
        this.forge = forge;
    }

    @Override
    protected void init() {
        clearChildren();
        prepareFrameState();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        prepareFrameState();
        pruneHoverState();

        context.fillGradient(0, 0, width, height, FlowGuiStyle.ROOT_BG_TOP, FlowGuiStyle.ROOT_BG_BOTTOM);
        context.fillGradient(0, 0, width, 84, FlowGuiStyle.ROOT_VIGNETTE_TOP, FlowGuiStyle.ROOT_VIGNETTE_BOTTOM);

        context.drawTextWithShadow(textRenderer, "Flow Module Manager v2", 12, 8, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "Search, filters, multi-select, batch actions, and delete confirmation.", 12, 20, FlowGuiStyle.TEXT_SECONDARY);

        drawToolbar(context, mouseX, mouseY, delta);
        drawModuleTable(context, mouseX, mouseY, delta);

        if (renameModalVisible) drawRenameModal(context, mouseX, mouseY, delta);
        if (confirmDeleteVisible) drawDeleteConfirmation(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
    }

    private void prepareFrameState() {
        updateLayout();
        rebuildFilteredList();
        syncSelectionWithFiltered();
        buildControls();
    }

    private void updateLayout() {
        int margin = 12;

        searchX = margin;
        searchY = 34;
        searchW = Math.max(180, Math.min(280, width - 430));
        searchH = SEARCH_H;

        tableX1 = margin;
        tableY1 = 88;
        tableX2 = width - margin;
        tableY2 = height - FOOTER_HEIGHT;
    }

    private void drawToolbar(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(8, 6, width - 8, 82, FlowGuiStyle.PANEL_TOP, FlowGuiStyle.PANEL_BOTTOM);
        drawRectOutline(context, 8, 6, width - 16, 76, FlowGuiStyle.PANEL_BORDER);

        drawSearchBox(context, mouseX, mouseY, delta);

        for (ControlButton control : controls) {
            drawControlButton(context, control, mouseX, mouseY, delta);
        }

        int selectedCount = selectedIds.size();
        int total = filtered.size();
        int critical = filteredSeverityCount(FlowGraphDiagnostics.HealthLevel.Critical);
        int warning = filteredSeverityCount(FlowGraphDiagnostics.HealthLevel.Warning);

        context.drawTextWithShadow(
            textRenderer,
            "Selected: " + selectedCount + " | Filtered: " + total + " | Critical: " + critical + " | Warning: " + warning,
            12,
            76,
            critical > 0 ? 0xFFFFBBA6 : (warning > 0 ? 0xFFF1D296 : (selectedCount > 0 ? 0xFFAEDCFF : FlowGuiStyle.TEXT_SECONDARY))
        );
    }

    private void drawSearchBox(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = inside(mouseX, mouseY, searchX, searchY, searchW, searchH);
        boolean focused = searchFocused;

        searchHoverProgress = FlowGuiStyle.animate(searchHoverProgress, hovered || focused, delta, 10f);
        int bg = FlowGuiStyle.blend(FlowGuiStyle.INPUT_IDLE, FlowGuiStyle.INPUT_HOVER, searchHoverProgress);
        if (focused) bg = FlowGuiStyle.blend(bg, FlowGuiStyle.INPUT_FOCUS, 0.82f);

        int border = FlowGuiStyle.blend(FlowGuiStyle.INPUT_BORDER_IDLE, FlowGuiStyle.INPUT_BORDER_HOVER, searchHoverProgress);
        if (focused) border = FlowGuiStyle.blend(border, FlowGuiStyle.INPUT_BORDER_FOCUS, 0.9f);

        context.fill(searchX, searchY, searchX + searchW, searchY + searchH, bg);
        drawRectOutline(context, searchX, searchY, searchW, searchH, border);

        String display = searchQuery.isEmpty() ? "Search modules..." : searchQuery;
        int textColor = searchQuery.isEmpty() ? FlowGuiStyle.TEXT_MUTED : FlowGuiStyle.TEXT_PRIMARY;

        context.drawTextWithShadow(textRenderer, display, searchX + 6, searchY + 5, textColor);

        if (focused && shouldDrawSearchCursor()) {
            int cursorX = searchX + 6;
            if (!searchQuery.isEmpty()) {
                clampSearchCursor();
                cursorX += textRenderer.getWidth(searchQuery.substring(0, searchCursorIndex));
            }
            if (cursorX < searchX + searchW - 4) {
                context.fill(cursorX, searchY + 4, cursorX + 1, searchY + searchH - 4, FlowGuiStyle.TEXT_PRIMARY);
            }
        }
    }

    private boolean shouldDrawSearchCursor() {
        long elapsed = Math.max(0, System.currentTimeMillis() - searchFocusStartedAt);
        return (elapsed / 450L) % 2L == 0L;
    }

    private void buildControls() {
        controls.clear();

        int gap = 6;
        int y1 = 10;
        int y2 = 34;
        int y3 = 58;

        int x = 12;
        addControl("new", "New Module", x, y1, 98, TOOLBAR_BUTTON_H, true, 0xFF2C5F8F, 0xFFBFE1FF);
        x += 98 + gap;

        addControl("open", "Open Forge", x, y1, 110, TOOLBAR_BUTTON_H, primarySelectionId() != -1, 0xFF355D86, 0xFFBFDFFF);
        x += 110 + gap;

        addControl("rename", "Rename", x, y1, 86, TOOLBAR_BUTTON_H, selectedIds.size() == 1, 0xFF4A4F7A, 0xFFD7DAFF);
        x += 86 + gap;

        addControl("sel-all", "Select All", x, y1, 92, TOOLBAR_BUTTON_H, !filtered.isEmpty(), 0xFF305F4A, 0xFFAEEEC8);
        x += 92 + gap;

        addControl("sel-clear", "Clear Sel", x, y1, 92, TOOLBAR_BUTTON_H, !selectedIds.isEmpty(), 0xFF4A5869, 0xFFD1DCE8);
        x += 92 + gap;

        addControl("panic-stop", "Panic Stop", x, y1, 100, TOOLBAR_BUTTON_H, true, 0xFF7A2F38, 0xFFFFC2CB);
        x += 100 + gap;

        addControl("reset-runtime", "Reset Stats", x, y1, 96, TOOLBAR_BUTTON_H, true, 0xFF2E5D7A, 0xFFC8E6FF);

        int closeW = 88;
        addControl("close", "Close", width - 12 - closeW, y1, closeW, TOOLBAR_BUTTON_H, true, 0xFF6A2F3B, 0xFFFFC1CC);

        int filterX = searchX + searchW + gap;
        addControl("filter-visible", "Visible: " + visibilityFilter.label, filterX, y2, 118, TOOLBAR_BUTTON_H, true, 0xFF30516C, 0xFFBFDFFF);
        filterX += 118 + gap;

        addControl("filter-runtime", "Runtime: " + runtimeFilter.label, filterX, y2, 122, TOOLBAR_BUTTON_H, true, 0xFF3A4C77, 0xFFC7D7FF);
        filterX += 122 + gap;

        addControl("sort", "Sort: " + sortMode.label, filterX, y2, 142, TOOLBAR_BUTTON_H, true, 0xFF4C4A79, 0xFFD7D4FF);

        int batchX = 12;
        boolean hasSelection = !selectedIds.isEmpty();
        addControl("batch-show", "Batch Show", batchX, y3, 88, TOOLBAR_BUTTON_H, hasSelection, 0xFF2F6A47, 0xFF97E9B8);
        batchX += 88 + gap;

        addControl("batch-hide", "Batch Hide", batchX, y3, 88, TOOLBAR_BUTTON_H, hasSelection, 0xFF6B4A2F, 0xFFF2C79A);
        batchX += 88 + gap;

        addControl("batch-run", "Batch Run", batchX, y3, 88, TOOLBAR_BUTTON_H, hasSelection, 0xFF236050, 0xFF8FF0D1);
        batchX += 88 + gap;

        addControl("batch-stop", "Batch Stop", batchX, y3, 88, TOOLBAR_BUTTON_H, hasSelection, 0xFF5F4F2A, 0xFFF2DA99);
        batchX += 88 + gap;

        addControl("batch-delete", "Delete Sel", batchX, y3, 98, TOOLBAR_BUTTON_H, hasSelection, 0xFF733038, 0xFFFFBBC4);
    }

    private void addControl(String id, String label, int x, int y, int w, int h, boolean enabled, int color, int textColor) {
        controls.add(new ControlButton(id, label, x, y, w, h, enabled, color, textColor));
    }

    private void drawControlButton(DrawContext context, ControlButton control, int mouseX, int mouseY, float delta) {
        boolean hovered = control.enabled && inside(mouseX, mouseY, control.x, control.y, control.w, control.h);
        int bg;
        int textColor;
        int border;

        if (!control.enabled) {
            bg = 0xFF25313F;
            textColor = FlowGuiStyle.TEXT_MUTED;
            border = 0xFF1B2633;
        } else {
            float hoverProgress = animateHover(controlHoverProgress, control.id, hovered, delta, 11f);
            bg = FlowGuiStyle.blend(control.color, FlowGuiStyle.brighten(control.color, 0.18f), hoverProgress);
            textColor = control.textColor;
            border = FlowGuiStyle.blend(0xFF0F1821, 0xFF9FC3E8, hoverProgress);
        }

        context.fill(control.x, control.y, control.x + control.w, control.y + control.h, bg);
        if (control.enabled) {
            float hoverProgress = controlHoverProgress.getOrDefault(control.id, 0f);
            int sheen = FlowGuiStyle.alpha(0xFFFFFF, (int) (26 * hoverProgress));
            context.fill(control.x + 1, control.y + 1, control.x + control.w - 1, control.y + 6, sheen);
        }
        drawRectOutline(context, control.x, control.y, control.w, control.h, border);

        int textX = control.x + (control.w - textRenderer.getWidth(control.label)) / 2;
        int textY = control.y + (control.h - 8) / 2;
        context.drawTextWithShadow(textRenderer, control.label, textX, textY, textColor);
    }

    private void drawModuleTable(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(tableX1, tableY1, tableX2, tableY2, FlowGuiStyle.SURFACE_SOFT);
        drawRectOutline(context, tableX1, tableY1, tableX2 - tableX1, tableY2 - tableY1, FlowGuiStyle.PANEL_BORDER);

        TableLayout layout = tableLayout();

        context.fillGradient(tableX1 + 1, tableY1 + 1, tableX2 - 1, tableY1 + 24, FlowGuiStyle.SURFACE_HEADER_TOP, FlowGuiStyle.SURFACE_HEADER_BOTTOM);
        context.drawTextWithShadow(textRenderer, "Sel", layout.selectionX, tableY1 + 8, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "Module", layout.nameX, tableY1 + 8, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "Graph / Health", layout.graphX + 4, tableY1 + 8, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "Visible", layout.visibleX + Math.max(8, (layout.statusW - textRenderer.getWidth("Visible")) / 2), tableY1 + 8, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "Runtime", layout.runtimeX + Math.max(8, (layout.statusW - textRenderer.getWidth("Runtime")) / 2), tableY1 + 8, FlowGuiStyle.TEXT_PRIMARY);
        int actionsTotalWidth = layout.actionW * 3 + ACTION_GAP * 2;
        int actionsTextX = layout.renameX + Math.max(0, (actionsTotalWidth - textRenderer.getWidth("Actions")) / 2);
        context.drawTextWithShadow(textRenderer, "Actions", actionsTextX, tableY1 + 8, FlowGuiStyle.TEXT_PRIMARY);

        int listStartY = tableY1 + HEADER_HEIGHT;
        int visibleRows = Math.max(1, (tableY2 - listStartY - 6) / ROW_HEIGHT);
        int maxScroll = Math.max(0, filtered.size() - visibleRows);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        int primaryId = primarySelectionId();

        for (int i = 0; i < visibleRows; i++) {
            int index = i + scroll;
            if (index >= filtered.size()) break;

            CustomFlowDefinition definition = filtered.get(index);
            boolean selected = selectedIds.contains(definition.id);
            boolean primary = primaryId == definition.id;
            boolean hoveredRow = inside(mouseX, mouseY, tableX1 + 1, listStartY + i * ROW_HEIGHT, tableX2 - tableX1 - 2, ROW_HEIGHT);

            int rowY = listStartY + i * ROW_HEIGHT;
            float rowHover = animateHover(rowHoverProgress, definition.id, hoveredRow, delta, 9f);
            int rowBg = (index % 2 == 0) ? FlowGuiStyle.SURFACE_SOFT : FlowGuiStyle.SURFACE_ALT;
            rowBg = FlowGuiStyle.blend(rowBg, FlowGuiStyle.brighten(rowBg, 0.14f), rowHover);
            if (selected) rowBg = FlowGuiStyle.blend(rowBg, primary ? 0x7A2C5D86 : 0x6A244B6E, primary ? 0.38f : 0.28f);
            context.fill(tableX1 + 1, rowY, tableX2 - 1, rowY + ROW_HEIGHT, rowBg);

            FlowGraphDiagnostics.Result diagnostics = diagnostics(definition);
            FlowGraphComplexity.Result complexity = complexity(definition);
            int healthColor = healthAccentColor(diagnostics.healthLevel());
            context.fill(tableX1 + 1, rowY, tableX1 + 3, rowY + ROW_HEIGHT, healthColor);

            if (selected) {
                int outline = primary ? 0xFF89D5FF : 0xFF6096C3;
                drawRectOutline(context, tableX1 + 1, rowY, tableX2 - tableX1 - 2, ROW_HEIGHT, outline);
            }

            int checkBg = selected ? (primary ? 0xFF2F86BD : 0xFF365F86) : 0xFF2A3543;
            context.fill(layout.selectionX, rowY + 8, layout.selectionX + 10, rowY + 18, checkBg);
            drawRectOutline(context, layout.selectionX, rowY + 8, 10, 10, 0xFF0D131A);

            String moduleText = textRenderer.trimToWidth(safeName(definition), layout.nameMaxWidth);
            context.drawTextWithShadow(textRenderer, moduleText, layout.nameX, rowY + 10, selected ? FlowGuiStyle.TEXT_PRIMARY : 0xFFD8E3F0);

            String graphInfo = diagnostics.shortGraphSummary()
                + " | " + diagnostics.healthLabel()
                + " | Cx " + complexity.score();
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(graphInfo, layout.graphMaxWidth), layout.graphX + 6, rowY + 10, healthColor);

            drawStatusBadge(
                context,
                layout.visibleX,
                rowY + 5,
                layout.statusW,
                definition.available ? "Visible" : "Hidden",
                definition.available ? 0xFF2E6945 : 0xFF5F3C3C,
                definition.available ? 0xFF8CE4AE : 0xFFE8A6A6,
                inside(mouseX, mouseY, layout.visibleX, rowY + 5, layout.statusW, STATUS_H),
                "vis-" + definition.id,
                delta
            );

            String runtimeLabel;
            int runtimeBg;
            int runtimeText;
            if (!definition.available) {
                runtimeLabel = "Disabled";
                runtimeBg = 0xFF3D4249;
                runtimeText = 0xFFC4CFDA;
            } else if (forge.isDefinitionRunning(definition.id)) {
                runtimeLabel = "Running";
                runtimeBg = 0xFF225B4A;
                runtimeText = 0xFF8EF0D0;
            } else {
                runtimeLabel = "Stopped";
                runtimeBg = 0xFF5E4E2B;
                runtimeText = 0xFFEED28B;
            }

            drawStatusBadge(
                context,
                layout.runtimeX,
                rowY + 5,
                layout.statusW,
                runtimeLabel,
                runtimeBg,
                runtimeText,
                inside(mouseX, mouseY, layout.runtimeX, rowY + 5, layout.statusW, STATUS_H),
                "run-" + definition.id,
                delta
            );
            drawActionButton(context, layout.renameX, rowY + 5, layout.actionW, ACTION_H, "Rename", mouseX, mouseY, 0xFF4D4A7F, 0xFFD8D5FF, "ren-" + definition.id, delta);
            drawActionButton(context, layout.editX, rowY + 5, layout.actionW, ACTION_H, "Edit", mouseX, mouseY, 0xFF2F5A84, 0xFF9ACCFF, "edit-" + definition.id, delta);
            drawActionButton(context, layout.deleteX, rowY + 5, layout.actionW, ACTION_H, "Delete", mouseX, mouseY, 0xFF6F3030, 0xFFFFB3B3, "del-" + definition.id, delta);
        }

        drawTableScrollbar(context, listStartY, visibleRows, maxScroll);

        int selectedCount = selectedIds.size();
        String hint = "Ctrl/Shift multi-select | Enter edit | F2 rename | Del confirm";
        int hintX = Math.max(tableX1 + 180, tableX2 - textRenderer.getWidth(hint) - 8);
        context.drawTextWithShadow(textRenderer, "Selected modules: " + selectedCount, tableX1 + 2, tableY2 + 8, FlowGuiStyle.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, hint, hintX, tableY2 + 8, FlowGuiStyle.TEXT_SECONDARY);
    }

    private void drawDeleteConfirmation(DrawContext context, int mouseX, int mouseY, float delta) {
        ModalLayout layout = modalLayout();

        context.fill(0, 0, width, height, 0xA0000000);
        context.fillGradient(layout.x1, layout.y1, layout.x2, layout.y2, FlowGuiStyle.PANEL_TOP, FlowGuiStyle.PANEL_BOTTOM);
        drawRectOutline(context, layout.x1, layout.y1, layout.x2 - layout.x1, layout.y2 - layout.y1, FlowGuiStyle.PANEL_BORDER);

        String headline = pendingDeleteIds.size() == 1 ? "Delete selected module?" : "Delete " + pendingDeleteIds.size() + " selected modules?";
        context.drawTextWithShadow(textRenderer, headline, layout.x1 + 12, layout.y1 + 14, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "This operation cannot be undone.", layout.x1 + 12, layout.y1 + 30, FlowGuiStyle.TEXT_SECONDARY);

        drawModalButton(context, layout.cancelX, layout.buttonY, layout.buttonW, ACTION_H, "Cancel", mouseX, mouseY, 0xFF3F4D61, 0xFFD5E1EF, "modal-cancel", delta);
        drawModalButton(context, layout.confirmX, layout.buttonY, layout.buttonW, ACTION_H, "Delete", mouseX, mouseY, 0xFF7A2F38, 0xFFFFC2CB, "modal-delete", delta);
    }

    private void drawRenameModal(DrawContext context, int mouseX, int mouseY, float delta) {
        RenameModalLayout layout = renameModalLayout();

        context.fill(0, 0, width, height, 0xA0000000);
        context.fillGradient(layout.x1, layout.y1, layout.x2, layout.y2, FlowGuiStyle.PANEL_TOP, FlowGuiStyle.PANEL_BOTTOM);
        drawRectOutline(context, layout.x1, layout.y1, layout.x2 - layout.x1, layout.y2 - layout.y1, FlowGuiStyle.PANEL_BORDER);

        context.drawTextWithShadow(textRenderer, "Rename custom module", layout.x1 + 12, layout.y1 + 14, FlowGuiStyle.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, "The final name is sanitized and kept unique.", layout.x1 + 12, layout.y1 + 28, FlowGuiStyle.TEXT_SECONDARY);

        boolean hoveredInput = inside(mouseX, mouseY, layout.inputX, layout.inputY, layout.inputW, layout.inputH);
        int inputBg = FlowGuiStyle.blend(FlowGuiStyle.INPUT_IDLE, FlowGuiStyle.INPUT_HOVER, hoveredInput ? 1f : 0f);
        if (renameInputFocused) inputBg = FlowGuiStyle.blend(inputBg, FlowGuiStyle.INPUT_FOCUS, 0.82f);
        int inputBorder = renameInputFocused ? FlowGuiStyle.INPUT_BORDER_FOCUS : (hoveredInput ? FlowGuiStyle.INPUT_BORDER_HOVER : FlowGuiStyle.INPUT_BORDER_IDLE);

        context.fill(layout.inputX, layout.inputY, layout.inputX + layout.inputW, layout.inputY + layout.inputH, inputBg);
        drawRectOutline(context, layout.inputX, layout.inputY, layout.inputW, layout.inputH, inputBorder);

        String display = renameValue.isEmpty() ? "custom-module-name" : renameValue;
        int textColor = renameValue.isEmpty() ? FlowGuiStyle.TEXT_MUTED : FlowGuiStyle.TEXT_PRIMARY;
        context.drawTextWithShadow(textRenderer, display, layout.inputX + 6, layout.inputY + 5, textColor);

        if (renameInputFocused && shouldDrawRenameCursor()) {
            int cursorX = layout.inputX + 6;
            if (!renameValue.isEmpty()) {
                clampRenameCursor();
                cursorX += textRenderer.getWidth(renameValue.substring(0, renameCursorIndex));
            }
            if (cursorX < layout.inputX + layout.inputW - 4) {
                context.fill(cursorX, layout.inputY + 4, cursorX + 1, layout.inputY + layout.inputH - 4, FlowGuiStyle.TEXT_PRIMARY);
            }
        }

        drawModalButton(context, layout.cancelX, layout.buttonY, layout.buttonW, ACTION_H, "Cancel", mouseX, mouseY, 0xFF3F4D61, 0xFFD5E1EF, "rename-cancel", delta);
        drawModalButton(context, layout.confirmX, layout.buttonY, layout.buttonW, ACTION_H, "Apply", mouseX, mouseY, 0xFF2F5E84, 0xFFBFE2FF, "rename-apply", delta);
    }

    private void drawModalButton(DrawContext context, int x, int y, int w, int h, String label, int mouseX, int mouseY, int baseColor, int textColor, String hoverKey, float delta) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        float hoverProgress = animateHover(modalHoverProgress, hoverKey, hovered, delta, 11f);
        int bg = FlowGuiStyle.blend(baseColor, FlowGuiStyle.brighten(baseColor, 0.16f), hoverProgress);

        context.fill(x, y, x + w, y + h, bg);
        drawRectOutline(context, x, y, w, h, FlowGuiStyle.blend(0xFF111A23, 0xFF9FC3E8, hoverProgress));

        int textX = x + (w - textRenderer.getWidth(label)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawTextWithShadow(textRenderer, label, textX, textY, textColor);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        prepareFrameState();

        double mx = click.x();
        double my = click.y();

        if (renameModalVisible) {
            return handleRenameModalClick(click.button(), mx, my);
        }

        if (confirmDeleteVisible) {
            return handleDeleteConfirmClick(click.button(), mx, my);
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (inside(mx, my, searchX, searchY, searchW, searchH)) {
                searchFocused = true;
                searchCursorIndex = cursorIndexAtSearchMouseX(mx);
                resetSearchCursorBlink();
                return true;
            }

            searchFocused = false;

            for (ControlButton control : controls) {
                if (!control.enabled) continue;
                if (!inside(mx, my, control.x, control.y, control.w, control.h)) continue;

                runControl(control.id);
                return true;
            }
        }

        return handleTableClick(click.button(), mx, my, doubled);
    }

    private boolean handleDeleteConfirmClick(int button, double mx, double my) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        ModalLayout layout = modalLayout();

        if (inside(mx, my, layout.confirmX, layout.buttonY, layout.buttonW, ACTION_H)) {
            confirmDelete();
            return true;
        }

        if (inside(mx, my, layout.cancelX, layout.buttonY, layout.buttonW, ACTION_H)) {
            cancelDelete();
            return true;
        }

        if (!inside(mx, my, layout.x1, layout.y1, layout.x2 - layout.x1, layout.y2 - layout.y1)) {
            cancelDelete();
            return true;
        }

        return true;
    }

    private boolean handleRenameModalClick(int button, double mx, double my) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        RenameModalLayout layout = renameModalLayout();
        if (inside(mx, my, layout.inputX, layout.inputY, layout.inputW, layout.inputH)) {
            renameInputFocused = true;
            renameCursorIndex = cursorIndexAtMouseX(renameValue, layout.inputX + 6, mx);
            resetRenameCursorBlink();
            return true;
        }

        renameInputFocused = false;

        if (inside(mx, my, layout.confirmX, layout.buttonY, layout.buttonW, ACTION_H)) {
            confirmRename();
            return true;
        }

        if (inside(mx, my, layout.cancelX, layout.buttonY, layout.buttonW, ACTION_H)) {
            cancelRename();
            return true;
        }

        if (!inside(mx, my, layout.x1, layout.y1, layout.x2 - layout.x1, layout.y2 - layout.y1)) {
            cancelRename();
            return true;
        }

        return true;
    }

    private boolean handleTableClick(int button, double mx, double my, boolean doubled) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        int listStartY = tableY1 + HEADER_HEIGHT;
        if (!inside(mx, my, tableX1, listStartY, tableX2 - tableX1, tableY2 - listStartY)) return false;

        int visibleRows = Math.max(1, (tableY2 - listStartY - 6) / ROW_HEIGHT);
        int row = (int) ((my - listStartY) / ROW_HEIGHT);
        if (row < 0 || row >= visibleRows) return false;

        int index = row + scroll;
        if (index < 0 || index >= filtered.size()) return false;

        CustomFlowDefinition definition = filtered.get(index);
        applySelectionFromClick(definition.id, index, controlKeyDown(), shiftKeyDown());

        int rowY = listStartY + row * ROW_HEIGHT;
        TableLayout layout = tableLayout();

        if (inside(mx, my, layout.visibleX, rowY + 5, layout.statusW, STATUS_H)) {
            forge.toggleDefinitionAvailable(definition.id);
            return true;
        }

        if (inside(mx, my, layout.runtimeX, rowY + 5, layout.statusW, STATUS_H)) {
            if (definition.available) forge.toggleDefinitionRunning(definition.id);
            return true;
        }

        if (inside(mx, my, layout.renameX, rowY + 5, layout.actionW, ACTION_H)) {
            promptRename(definition.id);
            return true;
        }

        if (inside(mx, my, layout.editX, rowY + 5, layout.actionW, ACTION_H)) {
            forge.openEditorForDefinition(definition.id);
            return true;
        }

        if (inside(mx, my, layout.deleteX, rowY + 5, layout.actionW, ACTION_H)) {
            promptDelete(List.of(definition.id));
            return true;
        }

        if (doubled) {
            forge.openEditorForDefinition(definition.id);
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        prepareFrameState();

        int listStartY = tableY1 + HEADER_HEIGHT;
        if (!inside(mouseX, mouseY, tableX1, listStartY, tableX2 - tableX1, tableY2 - listStartY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (verticalAmount > 0) scroll = Math.max(0, scroll - 1);
        if (verticalAmount < 0) scroll++;

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (renameModalVisible) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }

            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmRename();
                return true;
            }

            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                renameDeleteBeforeCursor();
                return true;
            }

            if (key == GLFW.GLFW_KEY_DELETE) {
                renameDeleteAfterCursor();
                return true;
            }

            if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_V && client != null && client.keyboard != null) {
                String clipboard = client.keyboard.getClipboard();
                if (clipboard != null && !clipboard.isBlank()) insertIntoRename(clipboard);
                return true;
            }

            if (key == GLFW.GLFW_KEY_LEFT) {
                moveRenameCursorByCodePoints(-1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_RIGHT) {
                moveRenameCursorByCodePoints(1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_HOME) {
                renameCursorIndex = 0;
                resetRenameCursorBlink();
                return true;
            }

            if (key == GLFW.GLFW_KEY_END) {
                renameCursorIndex = renameValue.length();
                resetRenameCursorBlink();
                return true;
            }

            return true;
        }

        if (confirmDeleteVisible) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelDelete();
                return true;
            }

            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmDelete();
                return true;
            }

            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }

            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }

            if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_A) {
                selectAllFiltered();
                return true;
            }

            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                deleteBeforeCursor();
                return true;
            }

            if (key == GLFW.GLFW_KEY_DELETE) {
                deleteAfterCursor();
                return true;
            }

            if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_V && client != null && client.keyboard != null) {
                String clipboard = client.keyboard.getClipboard();
                if (clipboard != null && !clipboard.isBlank()) {
                    insertIntoSearch(clipboard);
                }
                return true;
            }

            if (key == GLFW.GLFW_KEY_LEFT) {
                moveSearchCursorByCodePoints(-1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_RIGHT) {
                moveSearchCursorByCodePoints(1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_HOME) {
                searchCursorIndex = 0;
                resetSearchCursorBlink();
                return true;
            }

            if (key == GLFW.GLFW_KEY_END) {
                searchCursorIndex = searchQuery.length();
                resetSearchCursorBlink();
                return true;
            }

            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_F) {
            searchFocused = true;
            searchCursorIndex = searchQuery.length();
            resetSearchCursorBlink();
            return true;
        }

        if (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_A) {
            selectAllFiltered();
            return true;
        }

        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!selectedIds.isEmpty()) {
                promptDelete(selectionSnapshot());
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_F2 || (isControlDown(input.modifiers()) && key == GLFW.GLFW_KEY_R)) {
            int primary = primarySelectionId();
            if (primary != -1) {
                promptRename(primary);
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (key == GLFW.GLFW_KEY_UP) {
            movePrimarySelection(-1);
            return true;
        }

        if (key == GLFW.GLFW_KEY_DOWN) {
            movePrimarySelection(1);
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            int primary = primarySelectionId();
            if (primary != -1) {
                forge.openEditorForDefinition(primary);
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (renameModalVisible) {
            int cp = input.codepoint();
            if (Character.isISOControl(cp)) return true;
            insertIntoRename(input.asString());
            return true;
        }

        if (!searchFocused) return super.charTyped(input);

        int cp = input.codepoint();
        if (Character.isISOControl(cp)) return true;

        insertIntoSearch(input.asString());
        return true;
    }

    private void insertIntoSearch(String value) {
        if (value == null || value.isEmpty()) return;

        String cleaned = value.replace('\n', ' ').replace('\r', ' ');
        int remaining = SEARCH_MAX_LEN - searchQuery.codePointCount(0, searchQuery.length());
        if (remaining <= 0) return;

        String clipped = trimToCodePoints(cleaned, remaining);
        if (clipped.isEmpty()) return;

        clampSearchCursor();
        searchQuery = searchQuery.substring(0, searchCursorIndex) + clipped + searchQuery.substring(searchCursorIndex);
        searchCursorIndex += clipped.length();
        scroll = 0;
        resetSearchCursorBlink();
    }

    private void deleteBeforeCursor() {
        clampSearchCursor();
        if (searchCursorIndex <= 0 || searchQuery.isEmpty()) {
            resetSearchCursorBlink();
            return;
        }

        int start = searchQuery.offsetByCodePoints(searchCursorIndex, -1);
        searchQuery = searchQuery.substring(0, start) + searchQuery.substring(searchCursorIndex);
        searchCursorIndex = start;
        scroll = 0;
        resetSearchCursorBlink();
    }

    private void deleteAfterCursor() {
        clampSearchCursor();
        if (searchCursorIndex >= searchQuery.length() || searchQuery.isEmpty()) {
            resetSearchCursorBlink();
            return;
        }

        int end = searchQuery.offsetByCodePoints(searchCursorIndex, 1);
        searchQuery = searchQuery.substring(0, searchCursorIndex) + searchQuery.substring(end);
        scroll = 0;
        resetSearchCursorBlink();
    }

    private void moveSearchCursorByCodePoints(int delta) {
        if (delta == 0) return;

        clampSearchCursor();
        if (delta > 0 && searchCursorIndex < searchQuery.length()) {
            searchCursorIndex = searchQuery.offsetByCodePoints(searchCursorIndex, 1);
        } else if (delta < 0 && searchCursorIndex > 0) {
            searchCursorIndex = searchQuery.offsetByCodePoints(searchCursorIndex, -1);
        }

        resetSearchCursorBlink();
    }

    private int cursorIndexAtSearchMouseX(double mouseX) {
        return cursorIndexAtMouseX(searchQuery, searchX + 6, mouseX);
    }

    private int cursorIndexAtMouseX(String value, int textStartX, double mouseX) {
        if (value == null || value.isEmpty()) return 0;

        int relative = (int) Math.round(mouseX - textStartX);
        if (relative <= 0) return 0;

        int previousIndex = 0;
        int previousWidth = 0;
        int index = 0;

        while (index < value.length()) {
            int next = value.offsetByCodePoints(index, 1);
            int nextWidth = textRenderer.getWidth(value.substring(0, next));
            if (relative <= nextWidth) {
                int distPrev = Math.abs(relative - previousWidth);
                int distNext = Math.abs(nextWidth - relative);
                return distPrev <= distNext ? previousIndex : next;
            }

            previousIndex = next;
            previousWidth = nextWidth;
            index = next;
        }

        return value.length();
    }

    private void clampSearchCursor() {
        searchCursorIndex = MathHelper.clamp(searchCursorIndex, 0, searchQuery.length());
    }

    private void resetSearchCursorBlink() {
        searchFocusStartedAt = System.currentTimeMillis();
    }

    private boolean shouldDrawRenameCursor() {
        long elapsed = Math.max(0, System.currentTimeMillis() - renameFocusStartedAt);
        return (elapsed / 450L) % 2L == 0L;
    }

    private void insertIntoRename(String value) {
        if (value == null || value.isEmpty()) return;

        String cleaned = value.replace('\n', ' ').replace('\r', ' ');
        int remaining = RENAME_MAX_LEN - renameValue.codePointCount(0, renameValue.length());
        if (remaining <= 0) return;

        String clipped = trimToCodePoints(cleaned, remaining);
        if (clipped.isEmpty()) return;

        clampRenameCursor();
        renameValue = renameValue.substring(0, renameCursorIndex) + clipped + renameValue.substring(renameCursorIndex);
        renameCursorIndex += clipped.length();
        resetRenameCursorBlink();
    }

    private void renameDeleteBeforeCursor() {
        clampRenameCursor();
        if (renameCursorIndex <= 0 || renameValue.isEmpty()) {
            resetRenameCursorBlink();
            return;
        }

        int start = renameValue.offsetByCodePoints(renameCursorIndex, -1);
        renameValue = renameValue.substring(0, start) + renameValue.substring(renameCursorIndex);
        renameCursorIndex = start;
        resetRenameCursorBlink();
    }

    private void renameDeleteAfterCursor() {
        clampRenameCursor();
        if (renameCursorIndex >= renameValue.length() || renameValue.isEmpty()) {
            resetRenameCursorBlink();
            return;
        }

        int end = renameValue.offsetByCodePoints(renameCursorIndex, 1);
        renameValue = renameValue.substring(0, renameCursorIndex) + renameValue.substring(end);
        resetRenameCursorBlink();
    }

    private void moveRenameCursorByCodePoints(int delta) {
        if (delta == 0) return;

        clampRenameCursor();
        if (delta > 0 && renameCursorIndex < renameValue.length()) {
            renameCursorIndex = renameValue.offsetByCodePoints(renameCursorIndex, 1);
        } else if (delta < 0 && renameCursorIndex > 0) {
            renameCursorIndex = renameValue.offsetByCodePoints(renameCursorIndex, -1);
        }

        resetRenameCursorBlink();
    }

    private void clampRenameCursor() {
        renameCursorIndex = MathHelper.clamp(renameCursorIndex, 0, renameValue.length());
    }

    private void resetRenameCursorBlink() {
        renameFocusStartedAt = System.currentTimeMillis();
    }

    private static String trimToCodePoints(String value, int maxCodePoints) {
        if (value == null || value.isEmpty() || maxCodePoints <= 0) return "";
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) return value;

        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }

    private void runControl(String id) {
        switch (id) {
            case "new" -> {
                CustomFlowDefinition created = forge.createDefinition();
                selectedIds.clear();
                selectedIds.add(created.id);
                anchorSelectionId = created.id;
                forge.selectDefinition(created.id);
                scroll = 0;
            }
            case "open" -> {
                int primary = primarySelectionId();
                if (primary != -1) forge.openEditorForDefinition(primary);
            }
            case "rename" -> {
                int primary = primarySelectionId();
                if (primary != -1) promptRename(primary);
            }
            case "panic-stop" -> forge.panicStopAllDefinitions();
            case "reset-runtime" -> forge.resetAllRuntimeProfiles();
            case "close" -> close();
            case "sel-all" -> selectAllFiltered();
            case "sel-clear" -> {
                selectedIds.clear();
                anchorSelectionId = -1;
            }
            case "filter-visible" -> {
                visibilityFilter = visibilityFilter.next();
                scroll = 0;
            }
            case "filter-runtime" -> {
                runtimeFilter = runtimeFilter.next();
                scroll = 0;
            }
            case "sort" -> {
                sortMode = sortMode.next();
                scroll = 0;
            }
            case "batch-show" -> setSelectedVisible(true);
            case "batch-hide" -> setSelectedVisible(false);
            case "batch-run" -> setSelectedRunning(true);
            case "batch-stop" -> setSelectedRunning(false);
            case "batch-delete" -> promptDelete(selectionSnapshot());
            default -> {
            }
        }
    }

    private void selectAllFiltered() {
        selectedIds.clear();
        for (CustomFlowDefinition definition : filtered) {
            selectedIds.add(definition.id);
        }

        if (!filtered.isEmpty()) {
            anchorSelectionId = filtered.getFirst().id;
            forge.selectDefinition(anchorSelectionId);
        }
    }

    private void setSelectedVisible(boolean visible) {
        for (int definitionId : selectionSnapshot()) {
            forge.setDefinitionAvailable(definitionId, visible);
        }
    }

    private void setSelectedRunning(boolean running) {
        for (int definitionId : selectionSnapshot()) {
            CustomFlowDefinition definition = forge.getDefinitionById(definitionId);
            if (definition == null) continue;

            boolean currentlyRunning = forge.isDefinitionRunning(definitionId);
            if (running) {
                if (definition.available && !currentlyRunning) forge.toggleDefinitionRunning(definitionId);
            } else {
                if (currentlyRunning) forge.toggleDefinitionRunning(definitionId);
            }
        }
    }

    private void promptRename(int definitionId) {
        CustomFlowDefinition definition = forge.getDefinitionById(definitionId);
        if (definition == null) return;

        renameTargetId = definitionId;
        renameValue = safeName(definition);
        renameCursorIndex = renameValue.length();
        renameInputFocused = true;
        renameModalVisible = true;
        resetRenameCursorBlink();
    }

    private void cancelRename() {
        renameModalVisible = false;
        renameTargetId = -1;
        renameValue = "";
        renameCursorIndex = 0;
        renameInputFocused = false;
    }

    private void confirmRename() {
        if (renameTargetId == -1) {
            cancelRename();
            return;
        }

        String updatedName = forge.renameDefinition(renameTargetId, renameValue);
        cancelRename();
        if (updatedName != null) {
            prepareFrameState();
        }
    }

    private void promptDelete(List<Integer> definitionIds) {
        if (definitionIds == null || definitionIds.isEmpty()) return;

        pendingDeleteIds.clear();
        pendingDeleteIds.addAll(definitionIds);
        confirmDeleteVisible = true;
    }

    private void cancelDelete() {
        confirmDeleteVisible = false;
        pendingDeleteIds.clear();
    }

    private void confirmDelete() {
        List<Integer> ids = new ArrayList<>(pendingDeleteIds);
        cancelDelete();

        for (int definitionId : ids) {
            forge.deleteDefinition(definitionId);
            selectedIds.remove(definitionId);
            if (anchorSelectionId == definitionId) anchorSelectionId = -1;
        }

        scroll = 0;
        prepareFrameState();
    }

    private void movePrimarySelection(int delta) {
        if (filtered.isEmpty()) return;

        int current = primarySelectionId();
        int index = indexOfFiltered(current);
        if (index == -1) index = 0;

        int next = MathHelper.clamp(index + delta, 0, filtered.size() - 1);
        CustomFlowDefinition definition = filtered.get(next);

        selectedIds.clear();
        selectedIds.add(definition.id);
        anchorSelectionId = definition.id;
        forge.selectDefinition(definition.id);

        int listStartY = tableY1 + HEADER_HEIGHT;
        int visibleRows = Math.max(1, (tableY2 - listStartY - 6) / ROW_HEIGHT);
        if (next < scroll) scroll = next;
        if (next >= scroll + visibleRows) scroll = next - visibleRows + 1;
    }

    private void applySelectionFromClick(int definitionId, int filteredIndex, boolean controlDown, boolean shiftDown) {
        if (shiftDown && anchorSelectionId != -1) {
            int anchorIndex = indexOfFiltered(anchorSelectionId);
            if (anchorIndex == -1) anchorIndex = filteredIndex;

            int from = Math.min(anchorIndex, filteredIndex);
            int to = Math.max(anchorIndex, filteredIndex);

            if (!controlDown) selectedIds.clear();
            for (int i = from; i <= to; i++) {
                selectedIds.add(filtered.get(i).id);
            }
        } else if (controlDown) {
            if (selectedIds.contains(definitionId)) selectedIds.remove(definitionId);
            else selectedIds.add(definitionId);
            anchorSelectionId = definitionId;
        } else {
            selectedIds.clear();
            selectedIds.add(definitionId);
            anchorSelectionId = definitionId;
        }

        if (!selectedIds.isEmpty()) {
            forge.selectDefinition(definitionId);
        }
    }

    private int indexOfFiltered(int definitionId) {
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id == definitionId) return i;
        }

        return -1;
    }

    private void rebuildFilteredList() {
        filtered.clear();
        diagnosticsByDefinitionId.clear();
        complexityByDefinitionId.clear();

        String needle = searchQuery.trim().toLowerCase(Locale.ROOT);

        for (CustomFlowDefinition definition : forge.getDefinitions()) {
            diagnosticsByDefinitionId.put(definition.id, FlowGraphDiagnostics.analyze(definition));
            complexityByDefinitionId.put(definition.id, forge.complexityOfDefinition(definition.id));

            if (!matchesVisibility(definition)) continue;
            if (!matchesRuntime(definition)) continue;
            if (!needle.isEmpty() && !matchesSearch(definition, needle)) continue;
            filtered.add(definition);
        }

        filtered.sort(sortMode.comparator(this));
    }

    private boolean matchesSearch(CustomFlowDefinition definition, String needle) {
        if (safeName(definition).toLowerCase(Locale.ROOT).contains(needle)) return true;

        FlowGraphDiagnostics.Result diagnostics = diagnostics(definition);
        String graphInfo = definition.graph.nodes.size()
            + " "
            + definition.graph.links.size()
            + " "
            + definition.id
            + " "
            + diagnostics.healthLabel().toLowerCase(Locale.ROOT)
            + " "
            + diagnostics.healthScore()
            + " complexity "
            + complexity(definition).score()
            + " "
            + complexity(definition).level().name().toLowerCase(Locale.ROOT);
        return graphInfo.contains(needle);
    }

    private boolean matchesVisibility(CustomFlowDefinition definition) {
        return switch (visibilityFilter) {
            case All -> true;
            case Visible -> definition.available;
            case Hidden -> !definition.available;
        };
    }

    private boolean matchesRuntime(CustomFlowDefinition definition) {
        boolean running = forge.isDefinitionRunning(definition.id);

        return switch (runtimeFilter) {
            case All -> true;
            case Running -> running;
            case Stopped -> !running;
        };
    }

    private void syncSelectionWithFiltered() {
        Set<Integer> filteredIds = new LinkedHashSet<>();
        for (CustomFlowDefinition definition : filtered) {
            filteredIds.add(definition.id);
        }

        selectedIds.retainAll(filteredIds);

        if (selectedIds.isEmpty() && !filtered.isEmpty()) {
            CustomFlowDefinition selected = forge.getSelectedDefinition();
            int preferredId = selected == null ? -1 : selected.id;
            if (filteredIds.contains(preferredId)) selectedIds.add(preferredId);
            else selectedIds.add(filtered.getFirst().id);
        }

        if (!selectedIds.isEmpty()) {
            int primary = primarySelectionId();
            if (primary != -1) forge.selectDefinition(primary);

            if (!selectedIds.contains(anchorSelectionId)) {
                anchorSelectionId = primary;
            }
        } else {
            anchorSelectionId = -1;
        }
    }

    private int primarySelectionId() {
        if (selectedIds.isEmpty()) return -1;

        CustomFlowDefinition selected = forge.getSelectedDefinition();
        int current = selected == null ? -1 : selected.id;
        if (selectedIds.contains(current)) return current;

        return selectedIds.iterator().next();
    }

    private List<Integer> selectionSnapshot() {
        if (selectedIds.isEmpty()) return List.of();

        List<Integer> ordered = new ArrayList<>();
        for (CustomFlowDefinition definition : filtered) {
            if (selectedIds.contains(definition.id)) ordered.add(definition.id);
        }

        if (!ordered.isEmpty()) return ordered;
        return new ArrayList<>(selectedIds);
    }

    private ModalLayout modalLayout() {
        int w = Math.min(380, width - 40);
        int h = 110;

        int x1 = (width - w) / 2;
        int y1 = (height - h) / 2;
        int x2 = x1 + w;
        int y2 = y1 + h;

        int buttonW = 110;
        int buttonY = y2 - 28;
        int cancelX = x2 - 16 - buttonW * 2 - 8;
        int confirmX = x2 - 16 - buttonW;

        return new ModalLayout(x1, y1, x2, y2, cancelX, confirmX, buttonY, buttonW);
    }

    private RenameModalLayout renameModalLayout() {
        int w = Math.min(430, width - 40);
        int h = 136;

        int x1 = (width - w) / 2;
        int y1 = (height - h) / 2;
        int x2 = x1 + w;
        int y2 = y1 + h;

        int inputX = x1 + 12;
        int inputY = y1 + 44;
        int inputW = w - 24;
        int inputH = SEARCH_H;

        int buttonW = 110;
        int buttonY = y2 - 28;
        int cancelX = x2 - 16 - buttonW * 2 - 8;
        int confirmX = x2 - 16 - buttonW;
        return new RenameModalLayout(x1, y1, x2, y2, inputX, inputY, inputW, inputH, cancelX, confirmX, buttonY, buttonW);
    }

    private TableLayout tableLayout() {
        int tableWidth = Math.max(1, tableX2 - tableX1);
        int statusW = MathHelper.clamp(tableWidth / 10, 72, STATUS_W);
        int actionW = MathHelper.clamp(tableWidth / 12, 56, ACTION_W);

        int rightPadding = 10 + TABLE_SCROLLBAR_W + 4;
        int deleteX = tableX2 - rightPadding - actionW;
        int editX = deleteX - ACTION_GAP - actionW;
        int renameX = editX - ACTION_GAP - actionW;
        int runtimeX = renameX - 12 - statusW;
        int visibleX = runtimeX - 8 - statusW;

        int selectionX = tableX1 + 12;
        int nameX = tableX1 + 34;
        int graphX = Math.max(nameX + 96, visibleX - Math.max(140, tableWidth / 5));
        int nameMaxWidth = Math.max(70, graphX - nameX - 10);
        int graphMaxWidth = Math.max(70, visibleX - graphX - 10);

        return new TableLayout(selectionX, nameX, graphX, visibleX, runtimeX, renameX, editX, deleteX, nameMaxWidth, graphMaxWidth, statusW, actionW);
    }

    private void drawStatusBadge(
        DrawContext context,
        int x,
        int y,
        int w,
        String label,
        int bgColor,
        int textColor,
        boolean hovered,
        String hoverKey,
        float delta
    ) {
        float hoverProgress = animateHover(badgeHoverProgress, hoverKey, hovered, delta, 10f);
        int bg = FlowGuiStyle.blend(bgColor, FlowGuiStyle.brighten(bgColor, 0.2f), hoverProgress);
        int border = FlowGuiStyle.blend(0xFF101821, 0xFF89B2D7, hoverProgress);

        context.fill(x, y, x + w, y + STATUS_H, bg);
        drawRectOutline(context, x, y, w, STATUS_H, border);

        int textX = x + (w - textRenderer.getWidth(label)) / 2;
        int textY = y + (STATUS_H - 8) / 2;
        context.drawTextWithShadow(textRenderer, label, textX, textY, textColor);
    }

    private void drawActionButton(
        DrawContext context,
        int x,
        int y,
        int w,
        int h,
        String label,
        int mouseX,
        int mouseY,
        int baseColor,
        int textColor,
        String hoverKey,
        float delta
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        float hoverProgress = animateHover(actionHoverProgress, hoverKey, hovered, delta, 10f);
        int bg = FlowGuiStyle.blend(baseColor, FlowGuiStyle.brighten(baseColor, 0.18f), hoverProgress);

        context.fill(x, y, x + w, y + h, bg);
        int sheen = FlowGuiStyle.alpha(0xFFFFFF, (int) (20 * hoverProgress));
        context.fill(x + 1, y + 1, x + w - 1, y + 5, sheen);
        drawRectOutline(context, x, y, w, h, FlowGuiStyle.blend(0xFF0F1821, 0xFF91BCE2, hoverProgress));

        int textX = x + (w - textRenderer.getWidth(label)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawTextWithShadow(textRenderer, label, textX, textY, textColor);
    }

    private void drawTableScrollbar(DrawContext context, int listStartY, int visibleRows, int maxScroll) {
        int trackX = tableX2 - 1 - TABLE_SCROLLBAR_W;
        int trackY = listStartY + 1;
        int trackH = Math.max(8, visibleRows * ROW_HEIGHT - 2);

        context.fill(trackX, trackY, trackX + TABLE_SCROLLBAR_W, trackY + trackH, 0x66213044);
        drawRectOutline(context, trackX, trackY, TABLE_SCROLLBAR_W, trackH, 0xAA2D4560);

        if (maxScroll <= 0) {
            int idleHandle = Math.max(16, trackH / 3);
            int handleY = trackY + (trackH - idleHandle) / 2;
            context.fill(trackX + 1, handleY, trackX + TABLE_SCROLLBAR_W - 1, handleY + idleHandle, 0x6A6D8AA8);
            return;
        }

        float viewportRatio = Math.max(0.08f, Math.min(1f, visibleRows / (float) (visibleRows + maxScroll)));
        int handleH = Math.max(14, (int) (trackH * viewportRatio));
        int handleTravel = Math.max(1, trackH - handleH);
        int handleY = trackY + Math.round((scroll / (float) maxScroll) * handleTravel);

        context.fill(trackX + 1, handleY, trackX + TABLE_SCROLLBAR_W - 1, handleY + handleH, 0xD18EC5FF);
        context.fill(trackX + 1, handleY, trackX + TABLE_SCROLLBAR_W - 1, handleY + 3, 0xF0C5E7FF);
    }

    private float animateHover(Map<String, Float> stateMap, String key, boolean active, float delta, float responsiveness) {
        float current = stateMap.getOrDefault(key, 0f);
        float next = FlowGuiStyle.animate(current, active, delta, responsiveness);
        stateMap.put(key, next);
        return next;
    }

    private float animateHover(Map<Integer, Float> stateMap, int key, boolean active, float delta, float responsiveness) {
        float current = stateMap.getOrDefault(key, 0f);
        float next = FlowGuiStyle.animate(current, active, delta, responsiveness);
        stateMap.put(key, next);
        return next;
    }

    private void pruneHoverState() {
        Set<String> validControlIds = new HashSet<>();
        for (ControlButton control : controls) {
            validControlIds.add(control.id);
        }
        controlHoverProgress.keySet().retainAll(validControlIds);

        Set<Integer> validDefinitionIds = new HashSet<>();
        for (CustomFlowDefinition definition : forge.getDefinitions()) {
            validDefinitionIds.add(definition.id);
        }
        rowHoverProgress.keySet().retainAll(validDefinitionIds);
        actionHoverProgress.keySet().removeIf(key -> !containsValidDefinitionId(key, validDefinitionIds));
        badgeHoverProgress.keySet().removeIf(key -> !containsValidDefinitionId(key, validDefinitionIds));
    }

    private static boolean containsValidDefinitionId(String key, Set<Integer> validIds) {
        int idx = key.lastIndexOf('-');
        if (idx == -1 || idx + 1 >= key.length()) return true;
        try {
            int id = Integer.parseInt(key.substring(idx + 1));
            return validIds.contains(id);
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private void drawRectOutline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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

    private static boolean isControlDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
    }

    private FlowGraphDiagnostics.Result diagnostics(CustomFlowDefinition definition) {
        if (definition == null) return FlowGraphDiagnostics.Result.empty();
        return diagnosticsByDefinitionId.computeIfAbsent(definition.id, id -> FlowGraphDiagnostics.analyze(definition));
    }

    private FlowGraphComplexity.Result complexity(CustomFlowDefinition definition) {
        if (definition == null) return FlowGraphComplexity.Result.empty();
        return complexityByDefinitionId.computeIfAbsent(definition.id, id -> forge.complexityOfDefinition(id));
    }

    private int filteredSeverityCount(FlowGraphDiagnostics.HealthLevel level) {
        int count = 0;
        for (CustomFlowDefinition definition : filtered) {
            if (diagnostics(definition).healthLevel() == level) count++;
        }
        return count;
    }

    private static int healthAccentColor(FlowGraphDiagnostics.HealthLevel level) {
        return switch (level) {
            case Healthy -> 0xFF97E4B6;
            case Warning -> 0xFFF0D18A;
            case Critical -> 0xFFFFAE9D;
        };
    }

    private static int healthSeverity(FlowGraphDiagnostics.HealthLevel level) {
        return switch (level) {
            case Healthy -> 0;
            case Warning -> 1;
            case Critical -> 2;
        };
    }

    private record ControlButton(String id, String label, int x, int y, int w, int h, boolean enabled, int color, int textColor) {
    }

    private record ModalLayout(int x1, int y1, int x2, int y2, int cancelX, int confirmX, int buttonY, int buttonW) {
    }

    private record RenameModalLayout(
        int x1,
        int y1,
        int x2,
        int y2,
        int inputX,
        int inputY,
        int inputW,
        int inputH,
        int cancelX,
        int confirmX,
        int buttonY,
        int buttonW
    ) {
    }

    private record TableLayout(
        int selectionX,
        int nameX,
        int graphX,
        int visibleX,
        int runtimeX,
        int renameX,
        int editX,
        int deleteX,
        int nameMaxWidth,
        int graphMaxWidth,
        int statusW,
        int actionW
    ) {
    }

    private enum VisibilityFilter {
        All("All"),
        Visible("On"),
        Hidden("Off");

        private final String label;

        VisibilityFilter(String label) {
            this.label = label;
        }

        private VisibilityFilter next() {
            int index = (ordinal() + 1) % values().length;
            return values()[index];
        }
    }

    private enum RuntimeFilter {
        All("All"),
        Running("Running"),
        Stopped("Stopped");

        private final String label;

        RuntimeFilter(String label) {
            this.label = label;
        }

        private RuntimeFilter next() {
            int index = (ordinal() + 1) % values().length;
            return values()[index];
        }
    }

    private enum SortMode {
        NameAsc("Name A-Z") {
            @Override
            Comparator<CustomFlowDefinition> comparator(FlowModuleManagerScreen screen) {
                return Comparator
                    .comparing(FlowModuleManagerScreen::safeName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(definition -> definition.id);
            }
        },
        NameDesc("Name Z-A") {
            @Override
            Comparator<CustomFlowDefinition> comparator(FlowModuleManagerScreen screen) {
                return Comparator
                    .comparing(FlowModuleManagerScreen::safeName, String.CASE_INSENSITIVE_ORDER)
                    .reversed()
                    .thenComparingInt(definition -> definition.id);
            }
        },
        NodesDesc("Nodes") {
            @Override
            Comparator<CustomFlowDefinition> comparator(FlowModuleManagerScreen screen) {
                return Comparator
                    .comparingInt((CustomFlowDefinition definition) -> definition.graph.nodes.size())
                    .reversed()
                    .thenComparing(FlowModuleManagerScreen::safeName, String.CASE_INSENSITIVE_ORDER);
            }
        },
        RunningFirst("Running") {
            @Override
            Comparator<CustomFlowDefinition> comparator(FlowModuleManagerScreen screen) {
                return Comparator
                    .comparing((CustomFlowDefinition definition) -> !screen.forge.isDefinitionRunning(definition.id))
                    .thenComparing(FlowModuleManagerScreen::safeName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(definition -> definition.id);
            }
        },
        ComplexityHigh("Complexity") {
            @Override
            Comparator<CustomFlowDefinition> comparator(FlowModuleManagerScreen screen) {
                return Comparator
                    .comparingInt((CustomFlowDefinition definition) -> screen.complexity(definition).score())
                    .reversed()
                    .thenComparing(FlowModuleManagerScreen::safeName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(definition -> definition.id);
            }
        },
        HealthWorst("Health") {
            @Override
            Comparator<CustomFlowDefinition> comparator(FlowModuleManagerScreen screen) {
                return Comparator
                    .comparingInt((CustomFlowDefinition definition) -> healthSeverity(screen.diagnostics(definition).healthLevel()))
                    .reversed()
                    .thenComparingInt(definition -> screen.diagnostics(definition).healthScore())
                    .thenComparing(FlowModuleManagerScreen::safeName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(definition -> definition.id);
            }
        };

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        private SortMode next() {
            int index = (ordinal() + 1) % values().length;
            return values()[index];
        }

        abstract Comparator<CustomFlowDefinition> comparator(FlowModuleManagerScreen screen);
    }

    private static String safeName(CustomFlowDefinition definition) {
        if (definition == null || definition.name == null) return "";
        return definition.name;
    }
}
