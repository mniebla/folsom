/*
 * Copyright (c) 2014-2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.folsom.retry;

import com.google.common.util.concurrent.Futures;
import com.spotify.folsom.GetResult;
import com.spotify.folsom.MemcacheClosedException;
import com.spotify.folsom.RawMemcacheClient;
import com.spotify.folsom.client.OpCode;
import com.spotify.folsom.client.binary.GetRequest;
import com.spotify.folsom.transcoder.StringTranscoder;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RetryingClientTest {

  private static final int OPAQUE = 123;

  public static final GetRequest GET_REQUEST = new GetRequest("key1", OpCode.GET, -1, OPAQUE);
  public static final GetRequest FAIL_REQUEST = new GetRequest("key2", OpCode.GET, -1, OPAQUE);

  @Test
  public void testSimple() throws Exception {
    RawMemcacheClient delegate = mock(RawMemcacheClient.class);

    when(delegate.send(GET_REQUEST))
            .thenReturn(Futures.<GetResult<byte[]>>immediateFailedFuture(new MemcacheClosedException("reason")))
            .thenReturn(Futures.immediateFuture(GetResult.success(StringTranscoder.UTF8_INSTANCE.encode("bar"), 123)));

    when(delegate.send(FAIL_REQUEST))
            .thenReturn(Futures.<GetResult<byte[]>>immediateFailedFuture(new MemcacheClosedException("reason1")))
            .thenReturn(Futures.<GetResult<byte[]>>immediateFailedFuture(new MemcacheClosedException("reason2")));

    when(delegate.isConnected()).thenReturn(true);

    RetryingClient retryingClient = new RetryingClient(delegate);

    assertEquals("bar", StringTranscoder.UTF8_INSTANCE.decode(retryingClient.send(GET_REQUEST).get().getValue()));

    try {
      retryingClient.send(FAIL_REQUEST).get();
      fail();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      assertEquals(MemcacheClosedException.class, cause.getClass());
      assertEquals("reason2", cause.getMessage());
    }

  }
}
