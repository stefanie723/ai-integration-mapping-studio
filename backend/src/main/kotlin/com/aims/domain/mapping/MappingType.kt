package com.aims.domain.mapping

enum class MappingType {
    /** 直接使用源字段 */
    DIRECT,
    /** 固定值 */
    CONSTANT,
    /** 源字段为空时使用默认值 */
    DEFAULT,
    /** 字典转换 */
    DICTIONARY,
    /** 表达式（预留） */
    EXPRESSION,
    /** 条件赋值（预留） */
    CONDITION,
    /** 调用其他接口查询（预留） */
    LOOKUP,
    /** 不进行映射 */
    IGNORE
}
