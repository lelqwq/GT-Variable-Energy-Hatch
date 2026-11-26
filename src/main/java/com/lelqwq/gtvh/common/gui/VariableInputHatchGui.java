package com.lelqwq.gtvh.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.lelqwq.gtvh.common.machine.VariableInputHatch;
import com.lelqwq.gtvh.common.machine.VariableTieredHatch;

import gregtech.common.gui.modularui.hatch.MTEHatchInputGui;

/** Adds the configurable-tier row below the stock fluid screen. */
public class VariableInputHatchGui extends MTEHatchInputGui {

    public VariableInputHatchGui(VariableInputHatch hatch) {
        super(hatch);
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + 24;
    }

    @Override
    public void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        VariableTierGui.registerSyncValues(syncManager, (VariableTieredHatch) machine);
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createBottomLeftCornerFlow(panel, syncManager).child(VariableTierGui.createSettings(syncManager));
    }
}
