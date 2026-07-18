package com.aims.domain.mapping

/**
 * Unified mapping row status for the Mapping Studio workbench (V0.2.1).
 */
enum class MappingStatus {
    /** AI 高置信推荐，但尚未人工确认 */
    AI_RECOMMENDED,

    /** AI 推荐结果置信度不足 */
    NEED_CONFIRM,

    /** 必填字段但当前没有有效配置 */
    REQUIRED_UNMAPPED,

    /** 已由用户确认 */
    CONFIRMED,

    /** 用户主动忽略 */
    IGNORED,

    /** 判断为系统管理字段，无需人工 Mapping */
    SYSTEM_FIELD,

    /** 普通可选字段，没有进行配置 */
    UNMAPPED
}
