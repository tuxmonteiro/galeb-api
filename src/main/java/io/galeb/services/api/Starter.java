package io.galeb.services.api;

import com.openvraas.services.cdi.WeldContext;

public class Starter {

    public static void main(String[] args) {

        WeldContext.INSTANCE.getBean(Api.class);

    }

}
