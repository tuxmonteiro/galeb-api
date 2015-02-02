package com.openvraas.services.manager;

import com.openvraas.services.cdi.WeldContext;

public class Starter {

    public static void main(String[] args) {

        WeldContext.INSTANCE.getBean(Manager.class);

    }

}
