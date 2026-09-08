package com.haojiyou.cnchar.common;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 描述: 支持的文件类型
 *
 * @author : best.xu
 */
public enum SupportFileType {
    //java文件
    JAVA("java"),
    //xml文件
    XML("xml"),
    //sql文件
    SQL("sql"),
    //html文件
    HTML("html"),
    //js文件
    JS("js"),
    //c++源文件（.cpp）
    CPP("cpp"),
    //c++源文件（.cc）
    CC("cc"),
    //c++源文件（.cxx）
    CXX("cxx"),
    //c++源文件（.c++）
    CPLUSPLUS("c++"),
    //c源文件（.c）
    C("c"),
    //头文件（既可能是c头文件，也可能是c++头文件）
    H("h"),
    //c++头文件（.hpp）
    HPP("hpp"),
    //c++头文件（.hh）
    HH("hh"),
    //c++头文件（.hxx）
    HXX("hxx"),
    //c++头文件（.h++）
    HPLUSPLUS("h++"),
    //c++模板实现文件（.tpp）
    TPP("tpp"),
    //c++内联实现文件（.inl）
    INL("inl"),
    //c++内联实现文件（.ipp）
    IPP("ipp"),
    TS("ts"),
    //typescript 文件
    TSX("tsx"),
    //config file
    PROPERTIES("properties"),
    //git 忽略文件
    GIT_IGNORE("gitignore"),
    //css文件
    CSS("css");


    private final String type;

    private static final Map<String, SupportFileType> LOOKUP = new HashMap<>();

    static {
        for (SupportFileType t : values()) {
            LOOKUP.put(t.type.toLowerCase(Locale.ROOT), t);
        }
    }

    SupportFileType(String type) {
        this.type = type;
    }

    public static SupportFileType getFileType(String fileExtension) {
        if (fileExtension == null) {
            return null;
        }
        return LOOKUP.get(fileExtension.toLowerCase(Locale.ROOT));
    }

    public String getType() {
        return type;
    }

}
