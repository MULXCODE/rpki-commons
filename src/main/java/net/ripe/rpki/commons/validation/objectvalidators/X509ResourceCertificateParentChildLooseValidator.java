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
package net.ripe.rpki.commons.validation.objectvalidators;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationOptions;
import net.ripe.rpki.commons.validation.ValidationResult;

import static net.ripe.rpki.commons.validation.ValidationString.RESOURCE_RANGE;
import static net.ripe.rpki.commons.validation.ValidationString.ROOT_INHERITS_RESOURCES;


public class X509ResourceCertificateParentChildLooseValidator extends X509CertificateParentChildValidator<X509ResourceCertificate> implements X509ResourceCertificateValidator {

    private final CertificateRepositoryObjectValidationContext context;

    X509ResourceCertificateParentChildLooseValidator(ValidationOptions options, ValidationResult result, X509Crl crl, CertificateRepositoryObjectValidationContext context) {
        super(options, result, context.getCertificate(), crl);
        this.context = context;
    }

    @Override
    public void validate(String location, X509ResourceCertificate certificate) {
        super.validate(location, certificate);
        verifyResources();
    }

    private void verifyResources() {
        final ValidationResult result = getValidationResult();
        final X509ResourceCertificate child = getChild();
        final IpResourceSet resources = context.getResources();
        final IpResourceSet childResourceSet = child.deriveResources(resources);

        if (child.isRoot()) {
            result.rejectIfTrue(child.isResourceSetInherited(), ROOT_INHERITS_RESOURCES);
        } else {
            if (!resources.contains(childResourceSet)) {
                IpResourceSet overclaiming = new IpResourceSet(childResourceSet);
                overclaiming.removeAll(resources);

                context.addOverclaiming(overclaiming);
                result.warnIfFalse(overclaiming.isEmpty(), RESOURCE_RANGE, overclaiming.toString());
            }
        }
    }

}
