package com.lelqwq.gtvh.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.lelqwq.gtvh.common.machine.VariableLaserSourceHatch;

import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/** Adds tier / rated / working current controls to the stock laser source hatch GUI. */
public class VariableLaserSourceGui extends MTEHatchBaseGui<VariableLaserSourceHatch> {

    public VariableLaserSourceGui(VariableLaserSourceHatch hatch) {
        super(hatch);
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + 40;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        VariableLaserGui.registerSyncValues(syncManager, machine);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createContentSection(panel, syncManager)
            .child(VariableLaserGui.createContent(syncManager, machine));
    }
}
