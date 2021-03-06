package com.stanfy.helium.internal.entities;

/**
 * Convenience class for transferring byte arrays.
 * The purpose is to avoid unnecessary casts <code>byte[] to Byte[]</code>
 * and boxing/unboxing.
 *
 * @author Nikolay Soroka (Stanfy - http://www.stanfy.com)
 */
public class ByteArrayEntity {

  private final byte[] bytes;

  public ByteArrayEntity(final byte[] bytes) {
    this.bytes = bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }

}
