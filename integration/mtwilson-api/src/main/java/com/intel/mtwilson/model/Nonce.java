/*
 * Copyright (C) 2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.model;

import com.intel.dcsg.cpg.validation.ObjectModel;
import java.math.BigInteger;
import org.apache.commons.codec.binary.Hex;
import com.fasterxml.jackson.annotation.JsonValue;
//import org.codehaus.jackson.annotate.JsonValue;

/**
 * XXX TODO this class is just a draft. It needs to be finished.
 * Intent is to be a thin wrapper around byte[] so that we have type safety
 * and readability in our Java code.
 * 
 * If support for base64 and hex is required they should be implemented as
 * Nonce.fromBase64 and Nonce.fromHex because a String constructor would be
 * ambiguous.
 * @author jbuhacoff
 */
public class Nonce extends ObjectModel {
    private byte[] data;
    public Nonce() {
        // TODO: default constructor should create a random number
    }
    public Nonce(byte[] array) {
        data = array;
    }
    public Nonce(BigInteger nonce) {
        data = nonce.toByteArray(); // XXX watch out for 2's complement representation
    }
    
    public byte[] toByteArray() { return data; }

    @Override
    protected void validate() {
//        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    /**
     * Returns a string representing the Digest in hexadecimal form.
     *
     * @see java.lang.Object#toString()
     */
    @JsonValue
    @Override
    public String toString() {
        return Hex.encodeHexString(data);
    }
    
}
