package com.rzm.myhotfix;

public class ErrorUtils {
    public static void throwAnError() {
        //int i = 4/0; ---- 这样写直接导致编译器编译失败，原因不详
        throw new NullPointerException("出错了");
    }
}
