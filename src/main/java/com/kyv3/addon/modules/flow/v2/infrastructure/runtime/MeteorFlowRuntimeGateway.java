package com.kyv3.addon.modules.flow.v2.infrastructure.runtime;

import com.kyv3.addon.Kyv3Addon;
import com.kyv3.addon.modules.flow.FlowForgeModule;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeGateway;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeProfile;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;
import com.kyv3.addon.modules.flow.v2.domain.FlowGraph;
import com.kyv3.addon.modules.flow.v2.domain.FlowLink;
import com.kyv3.addon.modules.flow.v2.domain.FlowNode;
import com.kyv3.addon.modules.flow.v2.domain.FlowNodeKind;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class MeteorFlowRuntimeGateway implements FlowRuntimeGateway {
    private final FlowForgeModule owner;
    private final IntSupplier maxActionsBudget;
    private final BooleanSupplier caseInsensitiveChatMatch;
    private final Map<Integer, RuntimeDefinitionModule> dynamicModules = new HashMap<>();
    private final Map<Integer, MutableRuntimeProfile> runtimeProfiles = new HashMap<>();

    public MeteorFlowRuntimeGateway(FlowForgeModule owner, IntSupplier maxActionsBudget, BooleanSupplier caseInsensitiveChatMatch) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.maxActionsBudget = Objects.requireNonNull(maxActionsBudget, "maxActionsBudget");
        this.caseInsensitiveChatMatch = Objects.requireNonNull(caseInsensitiveChatMatch, "caseInsensitiveChatMatch");
    }

    @Override
    public synchronized void upsertDefinition(FlowDefinition definition) {
        if (definition == null || definition.id() <= 0) return;

        FlowDefinition copy = definition.copy();
        copy.sanitize();

        RuntimeDefinitionModule module = dynamicModules.get(copy.id());
        if (module == null) {
            module = new RuntimeDefinitionModule(copy.id(), copy.name());
            dynamicModules.put(copy.id(), module);
        }

        module.setDefinition(copy);
        module.setDefinitionName(copy.name());
        getOrCreateProfile(copy.id(), copy.name()).setDefinitionName(copy.name());

        List<Module> group = Modules.get().getGroup(Kyv3Addon.CUSTOM_CATEGORY);

        if (copy.available()) {
            if (!group.contains(module)) group.add(module);
        } else {
            module.setRunningInternal(false);
            group.remove(module);
            Modules.get().sortModules();
            return;
        }

        if (copy.active() && !module.isActive()) module.setRunningInternal(true);
        if (!copy.active() && module.isActive()) module.setRunningInternal(false);

        Modules.get().sortModules();
    }

    @Override
    public synchronized void removeDefinition(int definitionId) {
        RuntimeDefinitionModule module = dynamicModules.remove(definitionId);
        if (module == null) return;

        module.setRunningInternal(false);
        Modules.get().getGroup(Kyv3Addon.CUSTOM_CATEGORY).remove(module);
        runtimeProfiles.remove(definitionId);
        Modules.get().sortModules();
    }

    @Override
    public synchronized boolean isDefinitionRunning(int definitionId) {
        RuntimeDefinitionModule module = dynamicModules.get(definitionId);
        return module != null && module.isActive();
    }

    @Override
    public synchronized void setDefinitionRunning(int definitionId, boolean running) {
        RuntimeDefinitionModule module = dynamicModules.get(definitionId);
        if (module == null) return;

        FlowDefinition definition = module.definition();
        if (definition == null || !definition.available()) {
            module.setRunningInternal(false);
            return;
        }

        module.setRunningInternal(running);
    }

    @Override
    public synchronized FlowRuntimeProfile runtimeProfile(int definitionId) {
        MutableRuntimeProfile profile = runtimeProfiles.get(definitionId);
        if (profile == null) return FlowRuntimeProfile.empty(definitionId, "");
        return profile.snapshot();
    }

    @Override
    public synchronized Map<Integer, FlowRuntimeProfile> runtimeProfiles() {
        if (runtimeProfiles.isEmpty()) return Map.of();

        Map<Integer, FlowRuntimeProfile> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Integer, MutableRuntimeProfile> entry : runtimeProfiles.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().snapshot());
        }

        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public synchronized void resetRuntimeProfile(int definitionId) {
        MutableRuntimeProfile profile = runtimeProfiles.get(definitionId);
        if (profile == null) return;
        profile.reset();
    }

    @Override
    public synchronized int resetAllRuntimeProfiles() {
        if (runtimeProfiles.isEmpty()) return 0;

        int reset = 0;
        for (MutableRuntimeProfile profile : runtimeProfiles.values()) {
            if (profile == null) continue;
            profile.reset();
            reset++;
        }

        return reset;
    }

    private MutableRuntimeProfile getOrCreateProfile(int definitionId, String definitionName) {
        MutableRuntimeProfile profile = runtimeProfiles.get(definitionId);
        if (profile != null) return profile;

        MutableRuntimeProfile created = new MutableRuntimeProfile(definitionId, definitionName);
        runtimeProfiles.put(definitionId, created);
        return created;
    }

    private final class RuntimeDefinitionModule extends Module {
        private final int definitionId;
        private final List<QueuedExecution> queue = new ArrayList<>();
        private final MutableRuntimeProfile profile;

        private long ticksSinceEnable;
        private String definitionName;
        private FlowDefinition definition;

        private int callbackSuppressionDepth;

        private RuntimeDefinitionModule(int definitionId, String definitionName) {
            super(Kyv3Addon.CUSTOM_CATEGORY, definitionName, "Custom module generated by Flow Forge.");
            this.definitionId = definitionId;
            this.definitionName = definitionName;
            this.profile = getOrCreateProfile(definitionId, definitionName);
            this.chatFeedback = false;
        }

        private void setDefinitionName(String definitionName) {
            this.definitionName = definitionName;
            profile.setDefinitionName(definitionName);
        }

        private void setDefinition(FlowDefinition definition) {
            this.definition = definition == null ? null : definition.copy();
        }

        private FlowDefinition definition() {
            return definition;
        }

        private FlowGraph graph() {
            FlowDefinition current = definition();
            return current == null ? null : current.graph();
        }

        private void setRunningInternal(boolean running) {
            withSuppressedCallbacks(() -> {
                if (running) {
                    if (!isActive()) enable();
                } else {
                    if (isActive()) disable();
                }
            });
        }

        private void withSuppressedCallbacks(Runnable action) {
            callbackSuppressionDepth++;
            try {
                action.run();
            } finally {
                callbackSuppressionDepth = Math.max(0, callbackSuppressionDepth - 1);
            }
        }

        @Override
        public void onActivate() {
            queue.clear();
            ticksSinceEnable = 0;
            profile.markActivated();

            if (callbackSuppressionDepth == 0) owner.onRuntimeDefinitionStateChanged();

            triggerEventNodes(FlowNodeKind.OnEnable, null);
        }

        @Override
        public void onDeactivate() {
            queue.clear();

            if (callbackSuppressionDepth == 0) owner.onRuntimeDefinitionStateChanged();
        }

        @Override
        public String getInfoString() {
            return definitionName;
        }

        @EventHandler
        private void onGameJoined(GameJoinedEvent event) {
            triggerEventNodes(FlowNodeKind.OnJoin, null);
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            long tickStart = System.nanoTime();
            ticksSinceEnable++;
            triggerTickEvents();
            RunQueueStats queueStats = runQueue();
            long tickNanos = Math.max(0L, System.nanoTime() - tickStart);
            profile.recordTick(tickNanos, queueStats.executedActions, queueStats.executedActionNanos, queue.size(), queueStats.watchdogTriggered);
        }

        @EventHandler
        private void onPacket(PacketEvent.Receive event) {
            if (!(event.packet instanceof GameMessageS2CPacket packet)) return;

            String message = packet.content().getString();
            if (message == null || message.isBlank()) return;

            triggerEventNodes(FlowNodeKind.OnChatMatch, message);
        }

        private void triggerTickEvents() {
            FlowGraph graph = graph();
            if (graph == null) return;

            for (FlowNode node : graph.nodesView()) {
                if (node.kind() != FlowNodeKind.OnTick) continue;

                int interval = Math.max(1, node.number());
                if (ticksSinceEnable % interval == 0) enqueueChildren(node.id(), 0);
            }
        }

        private void triggerEventNodes(FlowNodeKind kind, String payload) {
            FlowGraph graph = graph();
            if (graph == null) return;

            String normalizedPayload = payload;
            if (payload != null && caseInsensitiveChatMatch.getAsBoolean()) {
                normalizedPayload = payload.toLowerCase(Locale.ROOT);
            }

            for (FlowNode node : graph.nodesView()) {
                if (node.kind() != kind) continue;

                if (kind == FlowNodeKind.OnChatMatch) {
                    String needle = node.text() == null ? "" : node.text().trim();
                    if (needle.isEmpty()) continue;

                    String normalizedNeedle = caseInsensitiveChatMatch.getAsBoolean() ? needle.toLowerCase(Locale.ROOT) : needle;
                    if (normalizedPayload == null || !normalizedPayload.contains(normalizedNeedle)) continue;
                }

                enqueueChildren(node.id(), 0);
            }
        }

        private RunQueueStats runQueue() {
            if (queue.isEmpty()) return RunQueueStats.EMPTY;

            int actionsBudget = Math.max(1, maxActionsBudget.getAsInt());
            int watchdog = 0;
            int executedActions = 0;
            long executedActionNanos = 0L;

            while (!queue.isEmpty() && actionsBudget > 0 && watchdog < 4096) {
                watchdog++;

                QueuedExecution execution = queue.remove(0);
                if (execution.delayTicks > 0) {
                    execution.delayTicks--;
                    queue.add(execution);
                    continue;
                }

                FlowNode node = nodeById(execution.nodeId);
                if (node == null) continue;

                if (node.kind().isEvent()) {
                    enqueueChildren(node.id(), 0);
                    continue;
                }

                actionsBudget--;
                long actionStart = System.nanoTime();
                executeActionNode(node);
                executedActionNanos += Math.max(0L, System.nanoTime() - actionStart);
                executedActions++;
            }

            boolean watchdogTriggered = watchdog >= 4096 && !queue.isEmpty();
            if (watchdog >= 4096 && !queue.isEmpty()) {
                warning("Flow queue watchdog triggered (%s). Cleared %d pending actions.", definitionName, queue.size());
                queue.clear();
            }

            return new RunQueueStats(executedActions, executedActionNanos, watchdogTriggered);
        }

        private void executeActionNode(FlowNode node) {
            switch (node.kind()) {
                case SendMessage -> {
                    if (mc.player != null && mc.player.networkHandler != null && node.text() != null && !node.text().isBlank()) {
                        if (node.text().startsWith("/")) mc.player.networkHandler.sendChatCommand(node.text().substring(1));
                        else mc.player.networkHandler.sendChatMessage(node.text());
                    }
                    enqueueChildren(node.id(), 0);
                }
                case SendCommand -> {
                    if (mc.player != null && mc.player.networkHandler != null && node.text() != null && !node.text().isBlank()) {
                        String command = node.text().startsWith("/") ? node.text().substring(1) : node.text();
                        mc.player.networkHandler.sendChatCommand(command);
                    }
                    enqueueChildren(node.id(), 0);
                }
                case Notify -> {
                    if (node.text() != null && !node.text().isBlank()) info("[%s] %s", definitionName, node.text());
                    enqueueChildren(node.id(), 0);
                }
                case Wait -> enqueueChildren(node.id(), Math.max(1, node.number()));
                case ToggleModule -> {
                    toggleMeteorModule(node.text(), ToggleAction.Toggle);
                    enqueueChildren(node.id(), 0);
                }
                case EnableModule -> {
                    toggleMeteorModule(node.text(), ToggleAction.Enable);
                    enqueueChildren(node.id(), 0);
                }
                case DisableModule -> {
                    toggleMeteorModule(node.text(), ToggleAction.Disable);
                    enqueueChildren(node.id(), 0);
                }
                case Chance -> {
                    int chance = Math.max(0, Math.min(100, node.number()));
                    if (ThreadLocalRandom.current().nextInt(100) < chance) enqueueChildren(node.id(), 0);
                }
                case Repeat -> {
                    int repeats = Math.max(1, Math.min(64, node.number()));
                    for (int i = 0; i < repeats; i++) enqueueChildren(node.id(), 0);
                }
                case IfHealthBelow,
                     IfHealthAbove,
                     IfHungerBelow,
                     IfOnGround,
                     IfInWater,
                     IfSneaking,
                     IfSprinting,
                     IfMoving,
                     IfModuleActive,
                     IfHoldingItem,
                     IfDimensionContains,
                     IfTargetEntityContains -> {
                    if (checkCondition(node)) enqueueChildren(node.id(), 0);
                }
                case Jump -> {
                    if (mc.player != null) mc.player.jump();
                    enqueueChildren(node.id(), 0);
                }
                case SneakOn -> {
                    if (mc.player != null) mc.player.setSneaking(true);
                    enqueueChildren(node.id(), 0);
                }
                case SneakOff -> {
                    if (mc.player != null) mc.player.setSneaking(false);
                    enqueueChildren(node.id(), 0);
                }
                case SprintOn -> {
                    if (mc.player != null) mc.player.setSprinting(true);
                    enqueueChildren(node.id(), 0);
                }
                case SprintOff -> {
                    if (mc.player != null) mc.player.setSprinting(false);
                    enqueueChildren(node.id(), 0);
                }
                case UseMainHand -> {
                    if (mc.player != null && mc.interactionManager != null) mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    enqueueChildren(node.id(), 0);
                }
                case UseOffHand -> {
                    if (mc.player != null && mc.interactionManager != null) mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                    enqueueChildren(node.id(), 0);
                }
                case SwingMainHand -> {
                    if (mc.player != null) mc.player.swingHand(Hand.MAIN_HAND);
                    enqueueChildren(node.id(), 0);
                }
                case SwingOffHand -> {
                    if (mc.player != null) mc.player.swingHand(Hand.OFF_HAND);
                    enqueueChildren(node.id(), 0);
                }
                case AttackTargetEntity -> {
                    if (mc.player != null && mc.interactionManager != null && mc.crosshairTarget instanceof EntityHitResult ehr) {
                        mc.interactionManager.attackEntity(mc.player, ehr.getEntity());
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    enqueueChildren(node.id(), 0);
                }
                case DropSelectedStack -> {
                    if (mc.player != null) mc.player.dropSelectedItem(true);
                    enqueueChildren(node.id(), 0);
                }
                case DropSingleItem -> {
                    if (mc.player != null) mc.player.dropSelectedItem(false);
                    enqueueChildren(node.id(), 0);
                }
                case SwapHands -> {
                    if (mc.player != null && mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                    }
                    enqueueChildren(node.id(), 0);
                }
                case OpenInventory -> {
                    if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
                    enqueueChildren(node.id(), 0);
                }
                case CloseCurrentScreen -> {
                    mc.setScreen(null);
                    enqueueChildren(node.id(), 0);
                }
                case SelectHotbar -> {
                    if (mc.player != null) {
                        int slot = Math.max(1, Math.min(9, node.number()));
                        mc.player.getInventory().setSelectedSlot(slot - 1);
                    }
                    enqueueChildren(node.id(), 0);
                }
                case SelectNextHotbar -> {
                    if (mc.player != null) {
                        int slot = (mc.player.getInventory().getSelectedSlot() + 1) % 9;
                        mc.player.getInventory().setSelectedSlot(slot);
                    }
                    enqueueChildren(node.id(), 0);
                }
                case SelectPreviousHotbar -> {
                    if (mc.player != null) {
                        int slot = (mc.player.getInventory().getSelectedSlot() + 8) % 9;
                        mc.player.getInventory().setSelectedSlot(slot);
                    }
                    enqueueChildren(node.id(), 0);
                }
                case LookYaw -> {
                    if (mc.player != null) mc.player.setYaw(node.number());
                    enqueueChildren(node.id(), 0);
                }
                case LookPitch -> {
                    if (mc.player != null) mc.player.setPitch(Math.max(-90, Math.min(90, node.number())));
                    enqueueChildren(node.id(), 0);
                }
                case AddForwardVelocity -> {
                    if (mc.player != null) {
                        double amount = node.number() / 100.0;
                        double yawRad = Math.toRadians(mc.player.getYaw());
                        mc.player.addVelocity(-Math.sin(yawRad) * amount, 0, Math.cos(yawRad) * amount);
                    }
                    enqueueChildren(node.id(), 0);
                }
                case AddVerticalVelocity -> {
                    if (mc.player != null) mc.player.addVelocity(0, node.number() / 100.0, 0);
                    enqueueChildren(node.id(), 0);
                }
                case StopMotion -> {
                    if (mc.player != null) mc.player.setVelocity(0, 0, 0);
                    enqueueChildren(node.id(), 0);
                }
                default -> enqueueChildren(node.id(), 0);
            }
        }

        private boolean checkCondition(FlowNode node) {
            if (mc.player == null) return false;

            return switch (node.kind()) {
                case IfHealthBelow -> mc.player.getHealth() <= node.number();
                case IfHealthAbove -> mc.player.getHealth() >= node.number();
                case IfHungerBelow -> mc.player.getHungerManager().getFoodLevel() <= node.number();
                case IfOnGround -> mc.player.isOnGround();
                case IfInWater -> mc.player.isTouchingWater();
                case IfSneaking -> mc.player.isSneaking();
                case IfSprinting -> mc.player.isSprinting();
                case IfMoving -> {
                    double speed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
                    yield speed > node.number() / 100.0;
                }
                case IfModuleActive -> {
                    Module target = Modules.get().get(node.text() == null ? "" : node.text().trim());
                    yield target != null && target.isActive();
                }
                case IfHoldingItem -> {
                    String needle = (node.text() == null ? "" : node.text()).toLowerCase(Locale.ROOT).trim();
                    if (needle.isEmpty()) yield false;

                    String itemText = (Registries.ITEM.getId(mc.player.getMainHandStack().getItem()) + " " + mc.player.getMainHandStack().getName().getString()).toLowerCase(Locale.ROOT);
                    yield itemText.contains(needle);
                }
                case IfDimensionContains -> {
                    if (mc.world == null) yield false;

                    String needle = (node.text() == null ? "" : node.text()).toLowerCase(Locale.ROOT).trim();
                    if (needle.isEmpty()) yield false;

                    yield mc.world.getRegistryKey().getValue().toString().toLowerCase(Locale.ROOT).contains(needle);
                }
                case IfTargetEntityContains -> {
                    if (!(mc.crosshairTarget instanceof EntityHitResult ehr)) yield false;

                    String needle = (node.text() == null ? "" : node.text()).toLowerCase(Locale.ROOT).trim();
                    if (needle.isEmpty()) yield false;

                    String hay = (ehr.getEntity().getName().getString() + " " + Registries.ENTITY_TYPE.getId(ehr.getEntity().getType())).toLowerCase(Locale.ROOT);
                    yield hay.contains(needle);
                }
                default -> false;
            };
        }

        private void toggleMeteorModule(String moduleName, ToggleAction action) {
            if (moduleName == null || moduleName.isBlank()) return;

            Module target = Modules.get().get(moduleName.trim());
            if (target == null || target == this || target == owner) return;

            switch (action) {
                case Toggle -> target.toggle();
                case Enable -> target.enable();
                case Disable -> target.disable();
            }
        }

        private FlowNode nodeById(int nodeId) {
            FlowGraph graph = graph();
            return graph == null ? null : graph.getNodeById(nodeId);
        }

        private void enqueueChildren(int nodeId, int delayTicks) {
            FlowGraph graph = graph();
            if (graph == null) return;

            for (FlowLink link : graph.linksView()) {
                if (link.fromNodeId() != nodeId) continue;
                queue.add(new QueuedExecution(link.toNodeId(), delayTicks));
                profile.recordQueueSize(queue.size());
            }
        }
    }

    private record RunQueueStats(int executedActions, long executedActionNanos, boolean watchdogTriggered) {
        private static final RunQueueStats EMPTY = new RunQueueStats(0, 0, false);
    }

    private static final class MutableRuntimeProfile {
        private final int definitionId;
        private String definitionName;

        private long totalTicks;
        private long totalActions;
        private long totalActionNanos;
        private int lastTickActions;
        private long lastTickNanos;
        private int maxActionsInTick;
        private long maxTickNanos;
        private int queuePeak;
        private int watchdogTrips;
        private long lastActionTimestamp;

        private MutableRuntimeProfile(int definitionId, String definitionName) {
            this.definitionId = definitionId;
            this.definitionName = definitionName == null ? "" : definitionName;
        }

        private void setDefinitionName(String definitionName) {
            this.definitionName = definitionName == null ? "" : definitionName;
        }

        private void markActivated() {
            lastTickActions = 0;
            lastTickNanos = 0;
        }

        private void recordQueueSize(int queueSize) {
            if (queueSize > queuePeak) queuePeak = queueSize;
        }

        private void recordTick(long tickNanos, int executedActions, long executedActionNanos, int queueSize, boolean watchdogTriggered) {
            totalTicks++;
            totalActions += Math.max(0, executedActions);
            totalActionNanos += Math.max(0L, executedActionNanos);

            lastTickActions = Math.max(0, executedActions);
            lastTickNanos = Math.max(0L, tickNanos);
            lastActionTimestamp = executedActions > 0 ? System.currentTimeMillis() : lastActionTimestamp;

            if (lastTickActions > maxActionsInTick) maxActionsInTick = lastTickActions;
            if (lastTickNanos > maxTickNanos) maxTickNanos = lastTickNanos;
            if (queueSize > queuePeak) queuePeak = queueSize;
            if (watchdogTriggered) watchdogTrips++;
        }

        private void reset() {
            totalTicks = 0;
            totalActions = 0;
            totalActionNanos = 0;
            lastTickActions = 0;
            lastTickNanos = 0;
            maxActionsInTick = 0;
            maxTickNanos = 0;
            queuePeak = 0;
            watchdogTrips = 0;
            lastActionTimestamp = 0;
        }

        private FlowRuntimeProfile snapshot() {
            return new FlowRuntimeProfile(
                definitionId,
                definitionName,
                totalTicks,
                totalActions,
                totalActionNanos,
                lastTickActions,
                lastTickNanos,
                maxActionsInTick,
                maxTickNanos,
                queuePeak,
                watchdogTrips,
                lastActionTimestamp
            );
        }
    }

    private enum ToggleAction {
        Toggle,
        Enable,
        Disable
    }

    private static final class QueuedExecution {
        private final int nodeId;
        private int delayTicks;

        private QueuedExecution(int nodeId, int delayTicks) {
            this.nodeId = nodeId;
            this.delayTicks = Math.max(0, delayTicks);
        }
    }
}
