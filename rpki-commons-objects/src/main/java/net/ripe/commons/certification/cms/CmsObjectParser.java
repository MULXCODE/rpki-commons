package net.ripe.commons.certification.cms;

import static net.ripe.commons.certification.cms.CmsObject.*;
import static net.ripe.commons.certification.validation.ValidationString.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collection;

import net.ripe.commons.certification.validation.ValidationResult;
import net.ripe.commons.certification.x509cert.X509CertificateParser;
import net.ripe.commons.certification.x509cert.X509PlainCertificate;
import net.ripe.commons.certification.x509cert.X509PlainCertificateException;
import net.ripe.commons.certification.x509cert.X509ResourceCertificate;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


public abstract class CmsObjectParser {

    private byte[] encoded;

    private X509PlainCertificate certificate;

    private String contentType;

    private DateTime signingTime;

    private ValidationResult validationResult;

    private String location;

    protected CmsObjectParser() {
        validationResult = new ValidationResult();
    }

    protected CmsObjectParser(ValidationResult result) {
        this.validationResult = result;
    }

    public void parse(String location, byte[] encoded) { // NOPMD - ArrayIsStoredDirectly
        this.location = location;
        this.encoded = encoded;
        validationResult.push(location);
        parseCms();
    }

    protected byte[] getEncoded() {
        return encoded;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    protected X509PlainCertificate getCertificate() {
        return certificate;
    }

    protected X509ResourceCertificate getResourceCertificate() {
        if (!(certificate instanceof X509ResourceCertificate)) {
            throw new IllegalStateException("Cms object does not contain a Resource Certificate");
        }
        return (X509ResourceCertificate) certificate;
    }

    protected String getContentType() {
        return contentType;
    }

    protected DateTime getSigningTime() {
        return signingTime;
    }

    public abstract void decodeContent(DEREncodable encoded);

    private void parseCms() {
        CMSSignedDataParser sp = null;
        try {
            sp = new CMSSignedDataParser(encoded);
        } catch (CMSException e) {
            validationResult.isTrue(false, CMS_DATA_PARSING);
            return;
        }
        validationResult.isTrue(true, CMS_DATA_PARSING);

        parseContent(sp);
        parseCmsCertificate(sp);
        verifyCmsSigning(sp, certificate.getCertificate());
    }

    private void parseContent(CMSSignedDataParser sp) {
        contentType = sp.getSignedContent().getContentType();

        InputStream signedContentStream = sp.getSignedContent().getContentStream();
        ASN1InputStream asn1InputStream = new ASN1InputStream(signedContentStream);

        try {
            decodeContent(asn1InputStream.readObject());
        } catch (IOException e) {
            validationResult.isTrue(false, DECODE_CONTENT);
            return;
        }

        try {
            validationResult.isTrue(asn1InputStream.readObject() == null, ONLY_ONE_SIGNED_OBJECT);
            asn1InputStream.close();
        } catch (IOException e) {
            validationResult.isTrue(false, CMS_CONTENT_PARSING);
        }
    }

    private void parseCmsCertificate(CMSSignedDataParser sp) {
        Collection<? extends Certificate> certificates = extractCertificate(sp);

        if (!validationResult.notNull(certificates, GET_CERTS_AND_CRLS)) {
            return;
        }
        validationResult.isTrue(certificates.size() == 1, ONLY_ONE_CERT_ALLOWED);
        if (!validationResult.isTrue(certificates.iterator().next() instanceof X509Certificate, CERT_IS_X509CERT)) {
            return;
        }

        certificate = parseCertificate(certificates);

        validationResult.isTrue(certificate.isEe(), CERT_IS_EE_CERT);
        validationResult.notNull(certificate.getSubjectKeyIdentifier(), CERT_HAS_SKI);
    }

    private X509PlainCertificate parseCertificate(Collection<? extends Certificate> certificates) {
        X509CertificateParser<? extends X509PlainCertificate> parser = getCertificateParser();
        try {
            X509Certificate x509certificate = (X509Certificate) certificates.iterator().next();
            parser.parse(location, x509certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new X509PlainCertificateException(e);
        }
        return parser.getCertificate();
    }

    private Collection<? extends Certificate> extractCertificate(CMSSignedDataParser sp) {
        Collection<? extends Certificate> certificates;
        try {
            CertStore certs;
            certs = sp.getCertificatesAndCRLs("Collection", (String) null);
            certificates = certs.getCertificates(null);
        } catch (NoSuchAlgorithmException e) {
            certificates = null;
        } catch (NoSuchProviderException e) {
            certificates = null;
        } catch (CMSException e) {
            certificates = null;
        } catch (CertStoreException e) {
            certificates = null;
        }
        return certificates;
    }

    protected abstract X509CertificateParser<? extends X509PlainCertificate> getCertificateParser();

    private void verifyCmsSigning(CMSSignedDataParser sp, X509Certificate certificate) {
        // Note: validationResult field is updated by methods used here.

        SignerInformation signer = extractSingleCmsSigner(sp);
        if (signer == null) {
            return;
        }

        if (!verifySigner(signer, certificate)) {
            return;
        }

        if (!verifyAndStoreSigningTime(signer)) {
            return;
        }

        verifySignature(certificate, signer);
    }

    private SignerInformation extractSingleCmsSigner(CMSSignedDataParser sp) {
        SignerInformationStore signerStore = getSignerStore(sp);
        if (!validationResult.notNull(signerStore, GET_SIGNER_INFO)) {
            return null;
        }

        Collection<?> signers = signerStore.getSigners();
        if (!validationResult.isTrue(signers.size() == 1, ONLY_ONE_SIGNER)) {
            return null;
        }

        return (SignerInformation) signers.iterator().next();
    }

    private SignerInformationStore getSignerStore(CMSSignedDataParser sp) {
        try {
            return sp.getSignerInfos();
        } catch (CMSException e) {
            return null; // Caller will validate that the SignerInformationStore is not null
        }
    }

    private boolean verifySigner(SignerInformation signer, X509Certificate certificate) {
        validationResult.isTrue(DIGEST_ALGORITHM_OID.equals(signer.getDigestAlgOID()), DIGEST_ALGORITHM);
        validationResult.isTrue(ENCRYPTION_ALGORITHM_OID.equals(signer.getEncryptionAlgOID()), ENCRYPTION_ALGORITHM);
        if (!validationResult.notNull(signer.getSignedAttributes(), SIGNED_ATTRS_PRESENT)) {
            return false;
        }
        validationResult.notNull(signer.getSignedAttributes().get(CMSAttributes.contentType), CONTENT_TYPE_ATTR_PRESENT);
        validationResult.notNull(signer.getSignedAttributes().get(CMSAttributes.messageDigest), MSG_DIGEST_ATTR_PRESENT);
        SignerId signerId = signer.getSID();
        validationResult.isTrue(signerId.match(certificate), SIGNER_ID_MATCH);

        return true;
    }

    private boolean verifyAndStoreSigningTime(SignerInformation signer) {
        Attribute signingTimeAttibute = signer.getSignedAttributes().get(CMSAttributes.signingTime);
        if (!validationResult.notNull(signingTimeAttibute, SIGNING_TIME_ATTR_PRESENT)) {
            return false;
        }
        if (!validationResult.isTrue(signingTimeAttibute.getAttrValues().size() == 1, ONLY_ONE_SIGNING_TIME_ATTR)) {
            return false;
        }

        Time signingTimeDate = Time.getInstance(signingTimeAttibute.getAttrValues().getObjectAt(0));
        signingTime = new DateTime(signingTimeDate.getDate().getTime(), DateTimeZone.UTC);
        return true;
    }

    private void verifySignature(X509Certificate certificate, SignerInformation signer) {
        boolean errorOccured = false;
        try {
            validationResult.isTrue(signer.verify(certificate.getPublicKey(), (String) null), SIGNATURE_VERIFICATION);
        } catch (NoSuchAlgorithmException e) {
            errorOccured = true;
        } catch (NoSuchProviderException e) {
            errorOccured = true;
        } catch (CMSException e) {
            errorOccured = true;
        }

        if (errorOccured) {
            validationResult.isTrue(false, SIGNATURE_VERIFICATION);
        }
    }
}