package com.lelqwq.gtvh.common.gui;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.enums.Mods.GregTech;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

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
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.lelqwq.gtvh.common.machine.VariableLaserHatch;

import gregtech.api.enums.GTValues;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;

/** Shared MUI2 controls for the variable laser source and target hatches. */
public final class VariableLaserGui {

    private static final String TIER_SYNC = "gtvhLaserTier";
    private static final String RATED_SYNC = "gtvhLaserRated";
    private static final String WORKING_SYNC = "gtvhLaserWorking";

    private static final int BUTTON_SIZE = 18;
    private static final int VALUE_WIDTH = 92;
    private static final int VALUE_HEIGHT = 18;

    private static final UITexture OVERLAY_BUTTON_MINUS_LARGE = UITexture
        .fullImage(GregTech.ID, "gui/overlay_button/minus_large");

    private VariableLaserGui() {}

    public static void registerSyncValues(PanelSyncManager syncManager, VariableLaserHatch machine) {
        syncManager
            .syncValue(TIER_SYNC, new IntSyncValue(machine::getStructureTier, machine::setStructureTier).allowC2S());
        syncManager
            .syncValue(RATED_SYNC, new IntSyncValue(machine::getRatedAmperes, machine::setRatedAmperes).allowC2S());
        syncManager.syncValue(
            WORKING_SYNC,
            new IntSyncValue(machine::getWorkingAmperes, machine::setWorkingAmperes).allowC2S());
    }

    public static Flow createContent(PanelSyncManager syncManager, VariableLaserHatch machine) {
        IntSyncValue tierSync = syncManager.findSyncHandler(TIER_SYNC, IntSyncValue.class);
        IntSyncValue ratedSync = syncManager.findSyncHandler(RATED_SYNC, IntSyncValue.class);
        IntSyncValue workingSync = syncManager.findSyncHandler(WORKING_SYNC, IntSyncValue.class);

        Flow column = Flow.column()
            .coverChildren()
            .center()
            .childPadding(6);

        column.child(createTierRow(tierSync, ratedSync, workingSync, machine));
        column.child(createRatedRow(tierSync, ratedSync, workingSync, machine));
        column.child(createWorkingRow(tierSync, ratedSync, workingSync));

        return column;
    }

    private static Flow createTierRow(IntSyncValue tierSync, IntSyncValue ratedSync, IntSyncValue workingSync,
        VariableLaserHatch machine) {
        ButtonWidget<?> minus = stepButton(false, () -> {
            int next = VariableLaserHatch.clampTier(tierSync.getIntValue() - (Interactable.hasShiftDown() ? 4 : 1));
            if (next != tierSync.getIntValue()) {
                tierSync.setIntValue(next);
                ratedSync.setIntValue(machine.getRatedAmperes());
                workingSync.setIntValue(machine.getWorkingAmperes());
            }
        }, () -> tierTooltip(tierSync.getIntValue()));

        ButtonWidget<?> plus = stepButton(true, () -> {
            int next = VariableLaserHatch.clampTier(tierSync.getIntValue() + (Interactable.hasShiftDown() ? 4 : 1));
            if (next != tierSync.getIntValue()) {
                tierSync.setIntValue(next);
                ratedSync.setIntValue(machine.getRatedAmperes());
                workingSync.setIntValue(machine.getWorkingAmperes());
            }
        }, () -> tierTooltip(tierSync.getIntValue()));

        Widget<?> value = valueBox(
            () -> StatCollector.translateToLocal("gtvh.gui.structure_short") + " "
                + tierSync.getIntValue()
                + " · "
                + GTValues.VN[tierSync.getIntValue()],
            () -> tierTooltip(tierSync.getIntValue()));

        markDirty(tierSync, minus, plus, value);
        return Flow.row()
            .coverChildren()
            .childPadding(2)
            .child(minus)
            .child(value)
            .child(plus);
    }

    private static Flow createRatedRow(IntSyncValue tierSync, IntSyncValue ratedSync, IntSyncValue workingSync,
        VariableLaserHatch machine) {
        ButtonWidget<?> minus = stepButton(
            false,
            () -> stepRated(tierSync, ratedSync, workingSync, machine, false),
            () -> ratedTooltip(tierSync.getIntValue(), ratedSync.getIntValue()));
        ButtonWidget<?> plus = stepButton(
            true,
            () -> stepRated(tierSync, ratedSync, workingSync, machine, true),
            () -> ratedTooltip(tierSync.getIntValue(), ratedSync.getIntValue()));

        Widget<?> value = valueBox(
            () -> StatCollector.translateToLocal("gtvh.gui.laser_rated_short") + " "
                + formatNumber(ratedSync.getIntValue())
                + " A",
            () -> ratedTooltip(tierSync.getIntValue(), ratedSync.getIntValue()));

        markDirty(ratedSync, minus, plus, value);
        return Flow.row()
            .coverChildren()
            .childPadding(2)
            .child(minus)
            .child(value)
            .child(plus);
    }

    private static Flow createWorkingRow(IntSyncValue tierSync, IntSyncValue ratedSync, IntSyncValue workingSync) {
        TextFieldWidget field = new TextFieldWidget().value(workingSync)
            .numbersInt(() -> 0L, () -> (long) ratedSync.getIntValue())
            .formatAsInteger(true)
            .setTextAlignment(Alignment.CENTER)
            .setMaxLength(20)
            .scrollValues(1, 64, 4, 16)
            .width(VALUE_WIDTH)
            .tooltipDynamic(tooltip -> addWorkingTooltip(tooltip, tierSync.getIntValue(), workingSync.getIntValue()))
            .tooltipShowUpTimer(TOOLTIP_DELAY);

        Flow workingColumn = Flow.column()
            .coverChildren()
            .childPadding(2)
            .child(
                IKey.lang("gtvh.gui.working_amperage")
                    .asWidget()
                    .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE))
            .child(field);

        markDirty(workingSync, field);
        return workingColumn;
    }

    private static void stepRated(IntSyncValue tierSync, IntSyncValue ratedSync, IntSyncValue workingSync,
        VariableLaserHatch machine, boolean up) {
        int[] allowed = VariableLaserHatch.allowedAmperes(tierSync.getIntValue());
        int index = 0;
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i] == ratedSync.getIntValue()) {
                index = i;
                break;
            }
        }
        int nextIndex;
        if (Interactable.hasShiftDown()) {
            nextIndex = up ? allowed.length - 1 : 0;
        } else {
            nextIndex = Math.max(0, Math.min(allowed.length - 1, index + (up ? 1 : -1)));
        }
        int next = allowed[nextIndex];
        if (next != ratedSync.getIntValue()) {
            ratedSync.setIntValue(next);
            workingSync.setIntValue(machine.getWorkingAmperes());
        }
    }

    private static ButtonWidget<?> stepButton(boolean up, Runnable action, Supplier<List<String>> tooltip) {
        return new ButtonWidget<>().size(BUTTON_SIZE)
            .overlay(up ? GTGuiTextures.OVERLAY_BUTTON_PLUS_LARGE : OVERLAY_BUTTON_MINUS_LARGE)
            .tooltipDynamic(t -> addTooltipLines(t, tooltip.get()))
            .tooltipShowUpTimer(TOOLTIP_DELAY)
            .onMousePressed(mouseButton -> {
                action.run();
                return true;
            });
    }

    private static Widget<?> valueBox(Supplier<String> display, Supplier<List<String>> tooltip) {
        Widget<?> box = IKey.dynamic(display)
            .alignment(Alignment.CENTER)
            .asWidget()
            .size(VALUE_WIDTH, VALUE_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_TEXT_FIELD)
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE)
            .scale(0.8f)
            .tooltipDynamic(t -> addTooltipLines(t, tooltip.get()))
            .tooltipShowUpTimer(TOOLTIP_DELAY);
        return box;
    }

    private static void addTooltipLines(RichTooltip tooltip, List<String> lines) {
        for (String line : lines) {
            tooltip.addLine(line);
        }
    }

    private static void markDirty(IntSyncValue sync, Widget<?>... widgets) {
        sync.changeListener(() -> {
            for (Widget<?> widget : widgets) {
                widget.markTooltipDirty();
            }
        });
    }

    private static List<String> tierTooltip(int tier) {
        return Arrays.asList(
            StatCollector.translateToLocalFormatted(
                "gtvh.gui.laser_tier.tooltip",
                GTValues.VN[tier],
                formatNumber(GTValues.V[tier])),
            StatCollector.translateToLocal("gtvh.gui.laser_tier.shift"));
    }

    private static List<String> ratedTooltip(int tier, int rated) {
        return Arrays.asList(
            StatCollector.translateToLocalFormatted("gtvh.gui.laser_rated.tooltip", formatNumber(rated)),
            StatCollector.translateToLocal("gtvh.gui.laser_rated.shift"));
    }

    private static void addWorkingTooltip(RichTooltip tooltip, int tier, int working) {
        tooltip
            .addLine(StatCollector.translateToLocalFormatted("gtvh.gui.laser_working.tooltip", formatNumber(working)));
        tooltip.addLine(
            StatCollector.translateToLocalFormatted(
                "gtvh.gui.laser_working.throughput",
                formatNumber(VariableLaserHatch.throughput(tier, working))));
    }
}
