package com.lelqwq.gtvh.loader;

import com.lelqwq.gtvh.common.api.enums.MetaTileEntityID;
import com.lelqwq.gtvh.common.machine.VariableEnergyHatch;

/**
 * MTE 构造入口。
 * <p>
 * 必须在 GT 的 preload 窗口内执行：{@code CommonMetaTileEntity} 的构造器会自行注册到
 * {@code GregTechAPI.METATILEENTITIES}，并要求此时 {@code sPreloadStarted && !sPostloadStarted}
 * （{@code GTProxy} 在 preload 阶段置位，{@code CommonMetaTileEntity} 构造器内校验）。
 * 本模组声明 {@code required-after:gregtech}，因此在 preInit 构造即可。
 */
public final class MachineLoader {

    /** 可变能源仓单例（注册后由 GT 的 ItemMachines 以 damage = MTE ID 提供物品形态） */
    public static VariableEnergyHatch VARIABLE_ENERGY_HATCH;

    private MachineLoader() {}

    public static void run() {
        VARIABLE_ENERGY_HATCH = new VariableEnergyHatch(
            MetaTileEntityID.VARIABLE_ENERGY_HATCH.ID,
            "Variable Energy Hatch",
            "Variable Energy Hatch");
    }
}
