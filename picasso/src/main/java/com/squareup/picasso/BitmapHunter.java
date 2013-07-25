/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.content.ContentResolver.SCHEME_FILE;
import static android.provider.ContactsContract.Contacts;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;

abstract class BitmapHunter implements Runnable {

  public static final String MIME_TYPE_GIF = "image/gif";

  /**
   * Global lock for bitmap decoding to ensure that we are only are decoding one at a time. Since
   * this will only ever happen in background threads we help avoid excessive memory thrashing as
   * well as potential OOMs. Shamelessly stolen from Volley.
   */
  private static final Object DECODE_LOCK = new Object();

  static final int DEFAULT_RETRY_COUNT = 2;

  final Picasso picasso;
  final Dispatcher dispatcher;
  final Cache cache;
  final String key;
  final Uri uri;
  final List<Transformation> transformations;
  final List<Request> requests;
  final PicassoBitmapOptions options;
  final boolean skipCache;

  Image result;
  Future<?> future;
  Picasso.LoadedFrom loadedFrom;
  Exception exception;

  int retryCount = DEFAULT_RETRY_COUNT;

  BitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Request request) {
    this.picasso = picasso;
    this.dispatcher = dispatcher;
    this.cache = cache;
    this.key = request.getKey();
    this.uri = request.getUri();
    this.transformations = request.transformations;
    this.options = request.options;
    this.skipCache = request.skipCache;
    this.requests = new ArrayList<Request>(4);
    attach(request);
  }

  @Override public void run() {
    try {
      Thread.currentThread().setName(Utils.THREAD_PREFIX + getName());

      result = hunt();

      if (result == null) {
        dispatcher.dispatchFailed(this);
      } else {
        dispatcher.dispatchComplete(this);
      }
    } catch (IOException e) {
      exception = e;
      dispatcher.dispatchRetry(this);
    } finally {
      Thread.currentThread().setName(Utils.THREAD_IDLE_NAME);
    }
  }

  abstract Image decode(Uri uri, PicassoBitmapOptions options, int retryCount) throws IOException;

  abstract Picasso.LoadedFrom getLoadedFrom();

  Image hunt() throws IOException {
    Image image;

    if (!skipCache) {
      image = cache.get(key);
      if (image != null) {
        loadedFrom = MEMORY;
        return image;
      }
    }

    image = decode(uri, options, retryCount);

    if (image != null && image.isBitmap() && (options != null || transformations != null)) {
      synchronized (DECODE_LOCK) {
        if (options != null) {
          image.setBitmap(transformResult(options, image.getBitmap(), options.exifRotation));
        }
        if (transformations != null) {
          image.setBitmap(applyCustomTransformations(transformations, image.getBitmap()));
        }
      }
    }

    return image;

    /*
    // todo Remove
    byte[] imageBytes = null;
    String imgUrl = uri.toString();
    if (imgUrl.startsWith("http")) {
      try {
        Log.d(StatsSnapshot.TAG, "Trying to fully load " + imgUrl);
        DefaultHttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(imgUrl);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        Header contentType = entity.getContentType();

        if (contentType != null) {
          String contentTypeValue = contentType.getValue();
          Log.d(StatsSnapshot.TAG, "  Content type " + contentTypeValue);
          if (contentTypeValue.contains(MIME_TYPE_GIF)) {
            Log.d(StatsSnapshot.TAG, "  Looks like a gif, loading " + contentTypeValue);
            int imageLength = (int) (entity.getContentLength());
            InputStream is = entity.getContent();

            imageBytes = new byte[imageLength];
            int bytesRead = 0;
            while (bytesRead < imageLength) {
              int n = is.read(imageBytes, bytesRead, imageLength - bytesRead);
              if (n <= 0)
                ; // do some error handling
              bytesRead += n;
            }
          }
        }
      } catch (IOException e) {
        Log.e(StatsSnapshot.TAG, "Error downloading gif bytes", e);
        return null;
      }
    }

    if (imageBytes != null) {
      Log.d(StatsSnapshot.TAG, "  Loaded and returning gif of size " + imageBytes.length);
      return new Image(imageBytes);
    } else {
      Bitmap bitmap = decode(uri, options, retryCount);

      if (bitmap != null && (options != null || transformations != null)) {
        synchronized (DECODE_LOCK) {
          if (options != null) {
            bitmap = transformResult(options, bitmap, options.exifRotation);
          }
          if (transformations != null) {
            bitmap = applyCustomTransformations(transformations, bitmap);
          }
        }
      }

      return new Image(bitmap);
    }
    */
  }

  // todo Remove
  private boolean isGifUri(Uri uri) {
    if (uri.toString().startsWith("http")) {
      DefaultHttpClient client = new DefaultHttpClient();
      HttpGet request = new HttpGet(uri.toString());
      try {
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        Header contentType = entity.getContentType();
        if (contentType != null) {
          String contentTypeValue = contentType.getValue();
          if (contentTypeValue.contains(MIME_TYPE_GIF)) {
            return true;
          }
        }
      } catch (IOException e) {
        Log.d(StatsSnapshot.TAG, "Unable to connect to image uri, can't determine if gif.");
        return false;
      } finally {
        // todo Apparently we don't need to close connections for HttpClient?
      }
    } else {
      // todo We might want to support file based gif uri's here, for now don't.
      return false;
    }
    return false;
  }

  void attach(Request request) {
    requests.add(request);
  }

  void detach(Request request) {
    requests.remove(request);
  }

  boolean cancel() {
    return requests.isEmpty() && future != null && future.cancel(false);
  }

  boolean isCancelled() {
    return future.isCancelled();
  }

  boolean shouldSkipCache() {
    return skipCache;
  }

  Image getResult() {
    return result;
  }

  String getKey() {
    return key;
  }

  String getName() {
    return uri.getPath();
  }

  static BitmapHunter forRequest(Context context, Picasso picasso, Dispatcher dispatcher,
                                 Cache cache, Request request, Downloader downloader, boolean airplaneMode) {
    if (request.getResourceId() != 0) {
      return new ResourceBitmapHunter(context, picasso, dispatcher, cache, request);
    }
    Uri uri = request.getUri();
    String scheme = uri.getScheme();
    if (SCHEME_CONTENT.equals(scheme)) {
      if (Contacts.CONTENT_URI.getHost().equals(uri.getHost()) //
          && !uri.getPathSegments().contains(Contacts.Photo.CONTENT_DIRECTORY)) {
        return new ContactsPhotoBitmapHunter(context, picasso, dispatcher, cache, request);
      } else {
        return new ContentProviderBitmapHunter(context, picasso, dispatcher, cache, request);
      }
    } else if (SCHEME_FILE.equals(scheme)) {
      return new FileBitmapHunter(context, picasso, dispatcher, cache, request);
    } else if (SCHEME_ANDROID_RESOURCE.equals(scheme)) {
      return new ResourceBitmapHunter(context, picasso, dispatcher, cache, request);
    } else {
      return new NetworkBitmapHunter(picasso, dispatcher, cache, request, downloader, airplaneMode);
    }
  }

  static void calculateInSampleSize(PicassoBitmapOptions options) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    final int reqHeight = options.targetHeight;
    final int reqWidth = options.targetWidth;
    int sampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
      final int heightRatio = Math.round((float) height / (float) reqHeight);
      final int widthRatio = Math.round((float) width / (float) reqWidth);
      sampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
    }

    options.inSampleSize = sampleSize;
    options.inJustDecodeBounds = false;
  }

  static Bitmap applyCustomTransformations(List<Transformation> transformations, Bitmap result) {
    for (int i = 0, count = transformations.size(); i < count; i++) {
      Transformation transformation = transformations.get(i);
      Bitmap newResult = transformation.transform(result);

      if (newResult == null) {
        StringBuilder builder = new StringBuilder() //
            .append("Transformation ")
            .append(transformation.key())
            .append(" returned null after ")
            .append(i)
            .append(" previous transformation(s).\n\nTransformation list:\n");
        for (Transformation t : transformations) {
          builder.append(t.key()).append('\n');
        }
        throw new NullPointerException(builder.toString());
      }

      if (newResult == result && result.isRecycled()) {
        throw new IllegalStateException(
            "Transformation " + transformation.key() + " returned input Bitmap but recycled it.");
      }

      // If the transformation returned a new bitmap ensure they recycled the original.
      if (newResult != result && !result.isRecycled()) {
        throw new IllegalStateException("Transformation "
            + transformation.key()
            + " mutated input Bitmap but failed to recycle the original.");
      }
      result = newResult;
    }
    return result;
  }

  static Bitmap transformResult(PicassoBitmapOptions options, Bitmap result, int exifRotation) {
    int inWidth = result.getWidth();
    int inHeight = result.getHeight();

    int drawX = 0;
    int drawY = 0;
    int drawWidth = inWidth;
    int drawHeight = inHeight;

    Matrix matrix = new Matrix();

    if (options != null) {
      int targetWidth = options.targetWidth;
      int targetHeight = options.targetHeight;

      float targetRotation = options.targetRotation;
      if (targetRotation != 0) {
        if (options.hasRotationPivot) {
          matrix.setRotate(targetRotation, options.targetPivotX, options.targetPivotY);
        } else {
          matrix.setRotate(targetRotation);
        }
      }

      if (options.centerCrop) {
        float widthRatio = targetWidth / (float) inWidth;
        float heightRatio = targetHeight / (float) inHeight;
        float scale;
        if (widthRatio > heightRatio) {
          scale = widthRatio;
          int newSize = (int) Math.ceil(inHeight * (heightRatio / widthRatio));
          drawY = (inHeight - newSize) / 2;
          drawHeight = newSize;
        } else {
          scale = heightRatio;
          int newSize = (int) Math.ceil(inWidth * (widthRatio / heightRatio));
          drawX = (inWidth - newSize) / 2;
          drawWidth = newSize;
        }
        matrix.preScale(scale, scale);
      } else if (options.centerInside) {
        float widthRatio = targetWidth / (float) inWidth;
        float heightRatio = targetHeight / (float) inHeight;
        float scale = widthRatio < heightRatio ? widthRatio : heightRatio;
        matrix.preScale(scale, scale);
      } else if (targetWidth != 0 && targetHeight != 0 //
          && (targetWidth != inWidth || targetHeight != inHeight)) {
        // If an explicit target size has been specified and they do not match the results bounds,
        // pre-scale the existing matrix appropriately.
        float sx = targetWidth / (float) inWidth;
        float sy = targetHeight / (float) inHeight;
        matrix.preScale(sx, sy);
      }

      float targetScaleX = options.targetScaleX;
      float targetScaleY = options.targetScaleY;
      if (targetScaleX != 0 || targetScaleY != 0) {
        matrix.setScale(targetScaleX, targetScaleY);
      }
    }

    if (exifRotation != 0) {
      matrix.preRotate(exifRotation);
    }

    Bitmap newResult =
        Bitmap.createBitmap(result, drawX, drawY, drawWidth, drawHeight, matrix, false);
    if (newResult != result) {
      result.recycle();
      result = newResult;
    }

    return result;
  }
}
