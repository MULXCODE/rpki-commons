/**
 * The BSD License
 *
 * Copyright (c) 2010-2020 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.commons.provisioning.payload;

import com.thoughtworks.xstream.XStream;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectBuilderException;
import net.ripe.rpki.commons.xml.XStreamXmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

class ProvisioningPayloadXmlSerializer<T extends AbstractProvisioningPayload> extends XStreamXmlSerializer<T> {

    public ProvisioningPayloadXmlSerializer(XStream xStream, Class<T> objectType) {
        super(xStream, objectType);
    }

    @Override
    public String serialize(T object) {
        try {
            return serializeUTF8Encoded(object);
        } catch (IOException e) {
            throw new ProvisioningCmsObjectBuilderException(e);
        }
    }

    private String serializeUTF8Encoded(T payload) throws IOException {
        final String xml;
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             final Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.write(System.getProperty("line.separator"));
            super.serialize(payload, writer);
            final String rawXml = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            xml = rawXml.replace("<message", "<message xmlns=\"http://www.apnic.net/specs/rescerts/up-down/\"");
        }
        return xml;
    }
}
