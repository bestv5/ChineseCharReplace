package com.haojiyou.cnchar.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 阶段0 特征化测试：锁定 {@link SupportFileType#getFileType(String)} 的现有行为。
 * 覆盖全部 23 个枚举值 + null + 未知扩展名 + 大小写不敏感。纯 JDK / 零平台依赖。
 */
class SupportFileTypeTest {

    @Test
    @DisplayName("枚举值数量为 23（现状）")
    void enumHas23Values() {
        assertEquals(23, SupportFileType.values().length);
    }

    @Test
    @DisplayName("getFileType: 每个枚举的 getType() 都能精确解析回自身")
    void everyTypeResolvesToItself() {
        for (SupportFileType type : SupportFileType.values()) {
            assertSame(type, SupportFileType.getFileType(type.getType()),
                    "扩展名应解析回自身: " + type.getType());
        }
    }

    @Test
    @DisplayName("getFileType: 23 个扩展名逐一显式断言映射")
    void explicitMappingForAll23() {
        assertSame(SupportFileType.JAVA, SupportFileType.getFileType("java"));
        assertSame(SupportFileType.XML, SupportFileType.getFileType("xml"));
        assertSame(SupportFileType.SQL, SupportFileType.getFileType("sql"));
        assertSame(SupportFileType.HTML, SupportFileType.getFileType("html"));
        assertSame(SupportFileType.JS, SupportFileType.getFileType("js"));
        assertSame(SupportFileType.CPP, SupportFileType.getFileType("cpp"));
        assertSame(SupportFileType.CC, SupportFileType.getFileType("cc"));
        assertSame(SupportFileType.CXX, SupportFileType.getFileType("cxx"));
        assertSame(SupportFileType.CPLUSPLUS, SupportFileType.getFileType("c++"));
        assertSame(SupportFileType.C, SupportFileType.getFileType("c"));
        assertSame(SupportFileType.H, SupportFileType.getFileType("h"));
        assertSame(SupportFileType.HPP, SupportFileType.getFileType("hpp"));
        assertSame(SupportFileType.HH, SupportFileType.getFileType("hh"));
        assertSame(SupportFileType.HXX, SupportFileType.getFileType("hxx"));
        assertSame(SupportFileType.HPLUSPLUS, SupportFileType.getFileType("h++"));
        assertSame(SupportFileType.TPP, SupportFileType.getFileType("tpp"));
        assertSame(SupportFileType.INL, SupportFileType.getFileType("inl"));
        assertSame(SupportFileType.IPP, SupportFileType.getFileType("ipp"));
        assertSame(SupportFileType.TS, SupportFileType.getFileType("ts"));
        assertSame(SupportFileType.TSX, SupportFileType.getFileType("tsx"));
        assertSame(SupportFileType.PROPERTIES, SupportFileType.getFileType("properties"));
        assertSame(SupportFileType.GIT_IGNORE, SupportFileType.getFileType("gitignore"));
        assertSame(SupportFileType.CSS, SupportFileType.getFileType("css"));
    }

    @Test
    @DisplayName("getFileType: 大小写不敏感")
    void caseInsensitive() {
        assertSame(SupportFileType.JAVA, SupportFileType.getFileType("JAVA"));
        assertSame(SupportFileType.JAVA, SupportFileType.getFileType("Java"));
        assertSame(SupportFileType.XML, SupportFileType.getFileType("XML"));
        assertSame(SupportFileType.GIT_IGNORE, SupportFileType.getFileType("GitIgnore"));
    }

    @Test
    @DisplayName("getFileType: null 返回 null")
    void nullReturnsNull() {
        assertNull(SupportFileType.getFileType(null));
    }

    @Test
    @DisplayName("getFileType: 未知扩展名返回 null")
    void unknownReturnsNull() {
        assertNull(SupportFileType.getFileType("xyz"));
        assertNull(SupportFileType.getFileType("txt"));
        assertNull(SupportFileType.getFileType("md"));
        assertNull(SupportFileType.getFileType("json"));
    }

    @Test
    @DisplayName("getFileType: 空串返回 null")
    void emptyReturnsNull() {
        assertNull(SupportFileType.getFileType(""));
    }

    @Test
    @DisplayName("getType: 返回构造时登记的扩展名")
    void getTypeReturnsRegisteredExtension() {
        assertEquals("gitignore", SupportFileType.GIT_IGNORE.getType());
        assertEquals("c++", SupportFileType.CPLUSPLUS.getType());
        assertEquals("h++", SupportFileType.HPLUSPLUS.getType());
    }
}
