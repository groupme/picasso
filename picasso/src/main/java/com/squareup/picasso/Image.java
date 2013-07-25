package com.squareup.picasso;

import android.graphics.Bitmap;

/**
 * Contains something stored in the cache. Can currently be a bitmap, or the bytes of an animated gif.
 */
public class Image {

  private Bitmap bitmap;
  private byte[] gifBytes;

  public Image(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  public Image(byte[] gifBytes) {
    this.gifBytes = gifBytes;
  }

  public Bitmap getBitmap() {
    return bitmap;
  }

  public void setBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  public byte[] getGifBytes() {
    return gifBytes;
  }

  public void setGifBytes(byte[] gifBytes) {
    this.gifBytes = gifBytes;
  }

  public boolean isBitmap() {
    return bitmap != null;
  }

  public boolean isGifBytes() {
    return gifBytes != null;
  }

  public int getSize() {
    if (isBitmap()) {
      return Utils.getBitmapBytes(bitmap);
    } else if (isGifBytes()) {
      return gifBytes.length;
    }
    return 0;
  }
}
