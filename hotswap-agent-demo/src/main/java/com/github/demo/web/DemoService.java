package com.github.demo.web;

import org.springframework.stereotype.Service;

@Service
public class DemoService {

    static {
        System.out.println("call static method");
    }

    public String service() {
        return "demo.service";
    }

}
