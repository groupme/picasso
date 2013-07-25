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

/**
 * A memory cache for storing the most recently used images.
 * <p/>
 * <em>Note:</em> The {@link Cache} is accessed by multiple threads. You must ensure
 * your {@link Cache} implementation is thread safe when {@link Cache#get(String)} or {@link
 * Cache#set(String, Image)} is called.
 */
public interface Cache {
  /** Retrieve an image for the specified {@code key} or {@code null}. */
  Image get(String key);

  /** Store an image in the cache for the specified {@code key}. */
  void set(String key, Image image);

  /** Returns the current size of the cache in bytes. */
  int size();

  /** Returns the maximum size in bytes that the cache can hold. */
  int maxSize();

  /** A cache which does not store any values. */
  Cache NONE = new Cache() {
    @Override public Image get(String key) {
      return null;
    }

    @Override public void set(String key, Image image) {
      // Ignore.
    }

    @Override public int size() {
      return 0;
    }

    @Override public int maxSize() {
      return 0;
    }
  };
}
