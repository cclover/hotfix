package com.example.chengchao.hotfixexample;

import com.example.testlibrary.LibraryTestClass;

/**
 * Created by chengchao on 2016/11/1.
 */

public class TestClass {

    public String show(){
        return "This is bug method";
    }

    public String function(){
        LibraryTestClass cl = new LibraryTestClass();
        return cl.function();
    }
}
