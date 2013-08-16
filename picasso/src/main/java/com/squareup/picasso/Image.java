package com.squareup.picasso;

import android.graphics.Bitmap;

/**
 * Contains something stored in the cache. Can currently be a bitmap, or the bytes of an animated gif.
 */
public class Image {

  private Bitmap bitmap;
  private byte[] bytes;
  private boolean isGif = false;

  public Image(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  public Image(Bitmap bitmap, byte[] bytes, boolean isGif) {
    this.bitmap = bitmap;
    this.bytes = bytes;
    this.isGif = isGif;
  }

  public Bitmap getBitmap() {
    return bitmap;
  }

  public void setBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public void setBytes(byte[] bytes) {
    this.bytes = bytes;
  }

  public boolean isBitmap() {
    return !isGif;
  }

  public boolean isGif() {
    return isGif;
  }

  public int getSize() {
    if (isBitmap()) {
      return Utils.getBitmapBytes(bitmap);
    } else if (isGif()) {
      return bytes.length;
    }
    return 0;
  }
}
