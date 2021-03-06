/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SnappyFramedDecoderTest {
    private EmbeddedByteChannel channel;

    @Before
    public void initChannel() {
        channel = new EmbeddedByteChannel(new SnappyFramedDecoder());
    }

    @Test(expected = CompressionException.class)
    public void testReservedUnskippableChunkTypeCausesError() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x03, 0x01, 0x00, 0x00, 0x00
        });

        channel.writeInbound(in);
    }

    @Test(expected = CompressionException.class)
    public void testInvalidStreamIdentifierLength() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x80, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        channel.writeInbound(in);
    }

    @Test(expected = CompressionException.class)
    public void testInvalidStreamIdentifierValue() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x80, 0x06, 0x00, 0x00, 's', 'n', 'e', 't', 't', 'y'
        });

        channel.writeInbound(in);
    }

    @Test(expected = CompressionException.class)
    public void testReservedSkippableBeforeStreamIdentifier() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x7f, 0x06, 0x00, 0x00, 's', 'n', 'e', 't', 't', 'y'
        });

        channel.writeInbound(in);
    }

    @Test(expected = CompressionException.class)
    public void testUncompressedDataBeforeStreamIdentifier() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x01, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        channel.writeInbound(in);
    }

    @Test(expected = CompressionException.class)
    public void testCompressedDataBeforeStreamIdentifier() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x00, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        channel.writeInbound(in);
    }

    @Test
    public void testReservedSkippableSkipsInput() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           -0x80, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
           -0x7f, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        channel.writeInbound(in);
        assertNull(channel.readInbound());

        assertFalse(in.isReadable());
    }

    @Test
    public void testUncompressedDataAppendsToOut() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           -0x80, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
            0x01, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        channel.writeInbound(in);

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] { 'n', 'e', 't', 't', 'y' });
        assertEquals(expected, channel.readInbound());
    }

    @Test
    public void testCompressedDataDecodesAndAppendsToOut() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           -0x80, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
            0x00, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                  0x05, // preamble length
                  0x04 << 2, // literal tag + length
                  0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        });

        channel.writeInbound(in);

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] { 'n', 'e', 't', 't', 'y' });
        assertEquals(expected, channel.readInbound());
    }
}
