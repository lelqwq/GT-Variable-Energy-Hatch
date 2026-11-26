package com.lelqwq.gtvh.common.gui;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.enums.Mods.GregTech;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.lelqwq.gtvh.common.machine.VariableTieredHatch;

import gregtech.api.enums.GTValues;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;

/** Builds the shared MUI2 tier row for the variable fluid hatches. */
public final class VariableTierGui {

    private static final String STRUCTURE_SYNC = "gtvhStructureTier";

    private static final int BUTTON_SIZE = 18;
    private static final int VALUE_WIDTH = 82;
    private static final int VALUE_HEIGHT = 18;

    private static final UITexture OVERLAY_BUTTON_MINUS_LARGE = UITexture
        .fullImage(GregTech.ID, "gui/overlay_button/minus_large");

    private VariableTierGui() {}

    public static void registerSyncValues(PanelSyncManager syncManager, VariableTieredHatch machine) {
        syncManager.syncValue(
            STRUCTURE_SYNC,
            new IntSyncValue(machine::getStructureTier, machine::setStructureTier).allowC2S());
    }

    public static Flow createSettings(PanelSyncManager syncManager) {
        return Flow.column()
            .coverChildren()
            .child(createTierRow(syncManager));
    }

    private static Flow createTierRow(PanelSyncManager syncManager) {
        IntSyncValue sync = syncManager.findSyncHandler(STRUCTURE_SYNC, IntSyncValue.class);

        ButtonWidget<?> minus = createStepButton(sync, false);
        ButtonWidget<?> plus = createStepButton(sync, true);

        Widget<?> value = IKey.dynamic(() -> valueText(sync))
            .alignment(Alignment.CENTER)
            .asWidget()
            .size(VALUE_WIDTH, VALUE_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_TEXT_FIELD)
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE)
            .scale(0.75f)
            .tooltipDynamic(tooltip -> addTooltip(tooltip, sync.getIntValue()))
            .tooltipShowUpTimer(TOOLTIP_DELAY);

        sync.changeListener(() -> {
            minus.markTooltipDirty();
            plus.markTooltipDirty();
            value.markTooltipDirty();
        });

        return Flow.row()
            .coverChildren()
            .childPadding(2)
            .child(minus)
            .child(value)
            .child(plus);
    }

    private static ButtonWidget<?> createStepButton(IntSyncValue sync, boolean up) {
        return new ButtonWidget<>().size(BUTTON_SIZE)
            .overlay(up ? GTGuiTextures.OVERLAY_BUTTON_PLUS_LARGE : OVERLAY_BUTTON_MINUS_LARGE)
            .tooltipDynamic(tooltip -> addTooltip(tooltip, sync.getIntValue()))
            .tooltipShowUpTimer(TOOLTIP_DELAY)
            .onMousePressed(mouseButton -> {
                int direction = up ? 1 : -1;
                int next = VariableTieredHatch.stepTier(sync.getIntValue(), direction, Interactable.hasShiftDown());
                if (next != sync.getIntValue()) sync.setIntValue(next);
                return true;
            });
    }

    private static String valueText(IntSyncValue sync) {
        int tier = sync.getIntValue();
        return StatCollector.translateToLocal("gtvh.gui.structure_short") + " " + tier + " · " + GTValues.VN[tier];
    }

    private static void addTooltip(RichTooltip tooltip, int tier) {
        tooltip.addLine(StatCollector.translateToLocalFormatted("gtvh.gui.structure_tier.tooltip", GTValues.VN[tier]));
        tooltip.addLine(
            StatCollector.translateToLocalFormatted(
                "gtvh.gui.fluid_capacity.tooltip",
                formatNumber(VariableTieredHatch.capacityForTier(tier))));
        tooltip.addLine(StatCollector.translateToLocal("gtvh.gui.shift_hint.tier"));
    }
}
