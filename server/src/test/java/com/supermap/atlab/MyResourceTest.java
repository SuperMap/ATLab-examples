package com.supermap.atlab;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTestNg;
import org.junit.Test;

import javax.ws.rs.core.Application;

import static org.junit.Assert.assertEquals;

public class MyResourceTest extends JerseyTestNg.ContainerPerClassTest {

    @Override
    protected Application configure() {
        return new ResourceConfig(MyResource.class);
    }

    @Test
    public void testGetIt() {
        final String hello = target("myresource").request().get(String.class);
        assertEquals("Got it!", hello);
    }
}