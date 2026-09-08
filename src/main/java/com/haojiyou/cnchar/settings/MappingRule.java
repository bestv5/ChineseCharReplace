package com.haojiyou.cnchar.settings;

/**
 * 描述: 单条自定义映射规则（from → to）的值对象，纯逻辑无 IDE 依赖。
 *
 * <p><b>序列化约束</b>：为兼容 IC-2020.3(203) 的 {@code XmlSerializer}，采用
 * <b>public 字段 + public 无参构造</b>（默认 Bean 序列化规则）。另提供全参构造供代码使用。
 * 逻辑上按“不可变值对象”使用：构造后不应再修改 {@link #from}/{@link #to}。
 *
 * <p>{@code from} 支持单字符与多字符键；多字符键用于光标前尾部兜底匹配
 * （受 {@link com.haojiyou.cnchar.convert.CharConverter#MAX_MULTI_CHAR_LEN} 约束）。
 *
 * @author : best.xu
 */
public class MappingRule {

    /** 源字符 / 字符序列（映射键）。 */
    public String from;
    /** 目标替换串（可为一对多，如 {@code …→...}）。 */
    public String to;

    /** XmlSerializer 需要的无参构造。 */
    public MappingRule() {
    }

    /** 全参构造，供代码使用。 */
    public MappingRule(String from, String to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MappingRule)) {
            return false;
        }
        MappingRule other = (MappingRule) o;
        return eq(from, other.from) && eq(to, other.to);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public int hashCode() {
        int result = from == null ? 0 : from.hashCode();
        result = 31 * result + (to == null ? 0 : to.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "MappingRule{" + from + " -> " + to + '}';
    }
}
