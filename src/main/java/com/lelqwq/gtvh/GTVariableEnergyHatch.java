package com.lelqwq.gtvh;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lelqwq.gtvh.loader.MachineLoader;
import com.lelqwq.gtvh.loader.RecipeLoader;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * 模组主类。
 * <p>
 * 只在 preInit 构造 MTE、init 注册配方，没有代理类（没有仅客户端代码）。
 */
@Mod(
    modid = GTVariableEnergyHatch.MODID,
    version = Tags.VERSION,
    name = "Variable Energy Hatch",
    acceptedMinecraftVersions = "[1.7.10]",
    // required-after:gregtech 是硬要求：MTE 必须构造在 GT preload 已开始、postload 尚未开始的窗口内
    dependencies = "required-after:gregtech;")
public class GTVariableEnergyHatch {

    public static final String MODID = "gtvariablehatch";

    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static GTVariableEnergyHatch instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MachineLoader.run();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RecipeLoader.run();
    }
}
