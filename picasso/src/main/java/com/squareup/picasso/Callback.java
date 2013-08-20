package com.squareup.picasso;

public interface Callback {
  void onSuccess(Image image);

  void onError();

  public static class EmptyCallback implements Callback {

    @Override public void onSuccess(Image image) {
    }

    @Override public void onError() {
    }
  }
}
