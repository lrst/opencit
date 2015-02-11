/*
 * Copyright (C) 2014 Intel Corporation
 * All rights reserved.
 */
package com.intel.dcsg.cpg.configuration;

import java.util.Iterator;

/**
 *
 * @author jbuhacoff
 */
public class CommonsConfigurationDecorator extends org.apache.commons.configuration.AbstractConfiguration {
    private Configuration configuration;

    public CommonsConfigurationDecorator(Configuration configuration) {
        super();
        this.configuration = configuration;
    }

    @Override
    protected void addPropertyDirect(String key, Object value) {
        if( value == null ) {
            configuration.set(key, null);
        }
        else if( value instanceof String ) {
            configuration.set(key, (String)value);
        }
        else {
            configuration.set(key, value.toString());
        }
    }

    @Override
    public boolean isEmpty() {
        return configuration.keys().isEmpty();
    }

    @Override
    public boolean containsKey(String key) {
        return configuration.keys().contains(key);
    }

    @Override
    public Object getProperty(String key) {
        return configuration.get(key, null);
    }

    @Override
    public Iterator<String> getKeys() {
        return configuration.keys().iterator();
    }
    

    
}
