package com.kyv3.addon.modules.flow.v2.domain;

public enum FlowNodeKind {
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
    private final String title;

    FlowNodeKind(boolean event, boolean supportsText, boolean supportsNumber, String title) {
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

    public String title() {
        return title;
    }
}
