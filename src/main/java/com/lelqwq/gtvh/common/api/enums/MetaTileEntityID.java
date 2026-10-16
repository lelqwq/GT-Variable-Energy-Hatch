package com.lelqwq.gtvh.common.api.enums;

/**
 * 元机器实体 (MTE) ID 分配。
 * <p>
 * 全局 ID 公式：{@code BASE(15000) + relativeId}。BASE 刻意避开 GT-Simple-Wireless-Network 的 14600 段。
 * <p>
 * 关于撞号：GT5U 的 {@code GregTechAPI.METATILEENTITIES} 是定长数组
 * （{@code MAXIMUM_METATILE_IDS = Short.MAX_VALUE - 1 = 32766}），而
 * {@code CommonMetaTileEntity} 的构造器在 ID 已被占用时会抛
 * {@code IllegalArgumentException("MetaTileEntity id X is already occupied! ...")}
 * —— 所以撞号表现为**启动崩溃 + 明确的占用提示**，不是静默覆盖。
 * 官方查空号方式：JVM 参数 {@code -Dgt.debug=true} 会列出所有已占用的 ID。
 */
public enum MetaTileEntityID {

    /** 可变能源仓 */
    VARIABLE_ENERGY_HATCH(0),

    ;

    /** ID 基准值 */
    private static final int BASE = 15000;

    /** 最终计算出的全局 MTE ID */
    public final int ID;

    MetaTileEntityID(int relativeId) {
        this.ID = BASE + relativeId;
    }
}
