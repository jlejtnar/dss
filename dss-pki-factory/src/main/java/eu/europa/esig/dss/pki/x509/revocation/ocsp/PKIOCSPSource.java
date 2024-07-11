/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 * 
 * This file is part of the "DSS - Digital Signature Services" project.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.pki.x509.revocation.ocsp;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.MaskGenerationFunction;
import eu.europa.esig.dss.enumerations.RevocationOrigin;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pki.exception.PKIException;
import eu.europa.esig.dss.pki.model.CertEntity;
import eu.europa.esig.dss.pki.model.CertEntityRevocation;
import eu.europa.esig.dss.pki.model.CertEntityRepository;
import eu.europa.esig.dss.spi.CertificateExtensionsUtils;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.spi.DSSRevocationUtils;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPSource;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPToken;
import eu.europa.esig.dss.utils.Utils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * The PkiOCSPSource class implements the OCSPSource interface for obtaining revocation tokens.
 * It retrieves OCSP responses for a given certificate by sending OCSP requests to a specified OCSP responder.
 */
public class PKIOCSPSource implements OCSPSource {

    private static final long serialVersionUID = 346675613204623498L;

    private static final Logger LOG = LoggerFactory.getLogger(PKIOCSPSource.class);

    /**
     * The repository managing the PKI issuing the OCSP responses
     */
    protected final CertEntityRepository certEntityRepository;

    /**
     * Certificate entity representing an OCSP issuer
     */
    private CertEntity ocspResponder;

    /**
     * Time of OCSP response signature
     */
    private Date producedAt;

    /**
     * ThisUpdate of the revocation status information
     */
    private Date thisUpdate;

    /**
     * NextUpdate of the revocation status information
     */
    private Date nextUpdate;

    /**
     * The Digest Algorithm of the signature of the OCSP response
     */
    private DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA512;

    /**
     * Encryption algorithm of the signature of the OCSP response
     */
    private EncryptionAlgorithm encryptionAlgorithm;

    /**
     * Defines whether a ResponderID property should be generated by the used Subject Key Identifier (SKI)
     */
    private boolean responderIdByKey = true;

    /**
     * Creates a PKIOCSPSource instance with OCSP issuer being provided on request issuer of certificate token.
     *
     * @param certEntityRepository {@link CertEntityRepository}
     */
    public PKIOCSPSource(final CertEntityRepository<? extends CertEntity> certEntityRepository) {
        Objects.requireNonNull(certEntityRepository, "Certificate repository shall be provided!");
        this.certEntityRepository = certEntityRepository;
    }

    /**
     * Creates a PKIOCSPSource instance with a defined {@code CertEntity} OCSP issuer
     *
     * @param certEntityRepository {@link CertEntityRepository}
     * @param ocspResponder {@link CertEntity} issuing OCSP responses.
     */
    public PKIOCSPSource(final CertEntityRepository<? extends CertEntity> certEntityRepository, final CertEntity ocspResponder) {
        this(certEntityRepository);
        this.ocspResponder = ocspResponder;
    }

    /**
     * Returns a producedAt time of the generated OCSP Response.
     * Returns {@code producedAt} if defined (see {@code #setProducedAt}). Otherwise, returns the current time.
     *
     * @return {@link Date}
     */
    protected Date getProducedAtTime() {
        if (producedAt == null) {
            return new Date();
        }
        return producedAt;
    }

    /**
     * Sets the production date for generating OCSP responses.
     * NOTE: updates thisUpdate parameter as well, if the latest is not set.
     *
     * @param producedAt The production date for OCSP responses.
     */
    public void setProducedAtTime(Date producedAt) {
        this.producedAt = producedAt;
        if (this.thisUpdate == null) {
            this.thisUpdate = producedAt;
        }
    }

    /**
     * Gets thisUpdate of revocation status information
     *
     * @return {@link Date}
     */
    protected Date getThisUpdate() {
        if (thisUpdate == null) {
            return new Date();
        }
        return thisUpdate;
    }

    /**
     * Sets thisUpdate of the revocation status information
     *
     * @param thisUpdate {@link Date}
     */
    public void setThisUpdate(Date thisUpdate) {
        this.thisUpdate = thisUpdate;
    }

    /**
     * Gets nextUpdate of revocation status information
     *
     * @return {@link Date}
     */
    protected Date getNextUpdate() {
        return nextUpdate;
    }

    /**
     * Sets nextUpdate of the revocation status information
     *
     * @param nextUpdate {@link Date}
     */
    public void setNextUpdate(Date nextUpdate) {
        this.nextUpdate = nextUpdate;
    }

    /**
     * Sets the digest algorithm to be used on OCSP response signature
     * Default: SHA512 (DigestAlgorithm.SHA512)
     *
     * @param digestAlgorithm {@link DigestAlgorithm}
     */
    public void setDigestAlgorithm(DigestAlgorithm digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    /**
     * Sets an encryption algorithm to be used on OCSP Response signing.
     * If not defined, the encryption algorithm from the given {@code CertEntity} OCSP responder will be used.
     * NOTE: It is important to ensure that the defined encryption algorithm is supported by the OCSP Responder.
     *
     * @param encryptionAlgorithm {@link EncryptionAlgorithm}
     */
    public void setEncryptionAlgorithm(EncryptionAlgorithm encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    /**
     * The mask generation function to be used on OCSP Response signing.
     * If not defined, no mask generation function will be used.
     *
     * @param maskGenerationFunction {@link MaskGenerationFunction}
     * @deprecated since DSS 6.1. Please use {@code setEncryptionAlgorithm} method
     *             to specify RSA (none MGF) or RSASSA-PSS (MGF1) algorithm
     */
    @Deprecated
    public void setMaskGenerationFunction(MaskGenerationFunction maskGenerationFunction) {
        if (EncryptionAlgorithm.RSASSA_PSS == encryptionAlgorithm && maskGenerationFunction == null) {
            setEncryptionAlgorithm(EncryptionAlgorithm.RSA);
        } else if (EncryptionAlgorithm.RSA == encryptionAlgorithm && MaskGenerationFunction.MGF1 == maskGenerationFunction) {
            setEncryptionAlgorithm(EncryptionAlgorithm.RSASSA_PSS);
        }
    }

    /**
     * Sets whether the ResponderID property should be generated based on the certificate's key
     * (SHA-1 of the OCSP responder's public key).
     * When set to FALSE, the ResponderID property will be generated based on OCSP responder's subject name.
     * Default: TRUE (the ResponderId is generated based on key)
     *
     * @param responderIdByKey  whether the ResponderID property should be generated based on the certificate's key
     */
    public void setResponderIdByKey(boolean responderIdByKey) {
        this.responderIdByKey = responderIdByKey;
    }

    /**
     * Returns a {@code CertEntity} to be used as an OCSP Response issuer.
     *
     * @param certificateToken {@link CertificateToken} to request OCSP Response for
     * @param issuerCertificateToken {@link CertificateToken} issued the {@code certificateToken}
     * @return {@link CertEntity} representing the entry to be used as an issuer of the OCSP Response
     */
    protected CertEntity getOcspResponder(CertificateToken certificateToken, CertificateToken issuerCertificateToken) {
        // If ocspResponder is not provided during construction, find it based on issuerCertificateToken and certificateToken
        CertEntity currentOCSPResponder;
        if (ocspResponder != null) {
            currentOCSPResponder = ocspResponder;
        } else {
            currentOCSPResponder = certEntityRepository.getByCertificateToken(issuerCertificateToken);
            if (currentOCSPResponder == null) {
                throw new PKIException(String.format("CertEntity for certificate token with Id '%s' " +
                                "not found in the repository! Provide a valid issuer or use #setOcspResponder method to set a custom OCSP responder.",
                        issuerCertificateToken.getDSSIdAsString()));
            }
        }
        return currentOCSPResponder;
    }

    /**
     * Sets the OCSP Responder {@code CertEntity} to sign the generated OCSP responses.
     * If not defined (or set to NULL), will use the certificate's issuer certificate as the OCSP responder.
     *
     * @param ocspResponder {@link CertEntity} to issue OCSP responses
     */
    public void setOcspResponder(CertEntity ocspResponder) {
        this.ocspResponder = ocspResponder;
    }

    /**
     * Retrieves the OCSP token for the revocation status of the given certificate.
     *
     * @param certificateToken       The CertificateToken representing the certificate for which the revocation status is to be checked.
     * @param issuerCertificateToken The CertificateToken representing the issuer certificate of the certificate to be verified.
     * @return An OCSPToken representing the OCSP response containing the revocation status of the certificate.
     */
    @Override
    public OCSPToken getRevocationToken(CertificateToken certificateToken, CertificateToken issuerCertificateToken) {
        Objects.requireNonNull(certificateToken, "Certificate cannot be null!");
        Objects.requireNonNull(issuerCertificateToken, "The issuer of the certificate to be verified cannot be null!");

        final String dssIdAsString = certificateToken.getDSSIdAsString();
        LOG.trace("--> PKIOCSPSource queried for {}", dssIdAsString);
        if (!canGenerate(certificateToken, issuerCertificateToken)) {
            return null;
        }

        try {
            OCSPReq ocspReq = buildOCSPRequest(certificateToken, issuerCertificateToken);
            OCSPResp ocspRespBytes = buildOCSPResponse(certificateToken, issuerCertificateToken, ocspReq);

            // Build the OCSP response and extract the latest single response
            BasicOCSPResp basicResponse = (BasicOCSPResp) ocspRespBytes.getResponseObject();
            SingleResp latestSingleResponse = DSSRevocationUtils.getLatestSingleResponse(basicResponse, certificateToken, issuerCertificateToken);

            // Create the OCSPToken using the OCSP response data
            OCSPToken ocspToken = new OCSPToken(basicResponse, latestSingleResponse, certificateToken, issuerCertificateToken);
            ocspToken.setExternalOrigin(RevocationOrigin.EXTERNAL);

            return ocspToken;

        } catch (OCSPException e) {
            throw new PKIException(String.format("Unable to build an OCSP response for certificate with Id '%s'. " +
                    "Reason : %s", certificateToken.getDSSIdAsString(), e.getMessage()), e);
        }
    }

    /**
     * Returns whether the current implementation is able to produce an OCSP response for the given {@code certificateToken}
     *
     * @param certificateToken {@link CertificateToken} to produce an OCSP response for
     * @param issuerCertificateToken {@link CertificateToken} representing an issuer of the {@code certificateToken}
     * @return TRUE if the current implementation is able to produce an OCSP response for the given pair, FALSE otherwise
     */
    protected boolean canGenerate(CertificateToken certificateToken, CertificateToken issuerCertificateToken) {
        List<String> ocspAccessUrls = CertificateExtensionsUtils.getOCSPAccessUrls(certificateToken);
        if (Utils.isCollectionEmpty(ocspAccessUrls)) {
            LOG.debug("No OCSP location found for {}", certificateToken.getDSSIdAsString());
            return false;
        }
        return true;
    }

    /**
     * Builds an OCSP Response
     *
     * @param certificateToken {@link CertificateToken} to get OCSP response for
     * @param issuerCertificateToken {@link CertificateToken} issuer of the {@code certificateToken}
     * @param ocspReq {@link OCSPReq} generated earlier
     * @return {@link OCSPResp}
     */
    protected OCSPResp buildOCSPResponse(CertificateToken certificateToken, CertificateToken issuerCertificateToken, OCSPReq ocspReq) {
        try {
            final CertEntity ocspResponderEntity = getOcspResponder(certificateToken, issuerCertificateToken);
            final BasicOCSPRespBuilder builder = initBuilder(ocspResponderEntity.getCertificateToken());

            CertEntityRevocation certRevocation = getCertificateTokenRevocation(certificateToken, ocspReq);
            addRevocationStatusToOCSPResponse(builder, ocspReq, certRevocation);

            SignatureAlgorithm signatureAlgorithm = getSignatureAlgorithm(ocspResponderEntity);
            PrivateKey ocspPrivateKey = ocspResponderEntity.getPrivateKey();
            ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm.getJCEId()).build(ocspPrivateKey);

            X509CertificateHolder[] x509CertificateHolders = ocspResponderEntity.getCertificateChain().stream()
                    .map(DSSASN1Utils::getX509CertificateHolder).toArray(X509CertificateHolder[]::new);
            Date producedAtTime = getProducedAtTime();
            BasicOCSPResp basicOCSPResp = builder.build(signer, x509CertificateHolders, producedAtTime);

            final OCSPRespBuilder respBuilder = new OCSPRespBuilder();
            return respBuilder.build(OCSPRespBuilder.SUCCESSFUL, basicOCSPResp);

        } catch (OperatorCreationException | OCSPException e) {
            throw new PKIException(String.format("Unable to generate the OCSP Response. Reason: %s", e.getMessage()), e);
        }
    }

    /**
     * Returns a revocation status for the given {@code CertificateToken} or a certificate within {@code OCSPReq}
     *
     * @param certificateToken {@link CertificateToken} to get revocation status for
     * @param ocspReq {@link OCSPReq}
     * @return {@link CertEntityRevocation}
     */
    @SuppressWarnings("unchecked")
    protected CertEntityRevocation getCertificateTokenRevocation(CertificateToken certificateToken, OCSPReq ocspReq) {
        CertEntity certEntity = certEntityRepository.getByCertificateToken(certificateToken);
        if (certEntity == null) {
            throw new PKIException(String.format("CertEntity for certificate token with Id '%s' " +
                    "not found in the repository!", certificateToken.getDSSIdAsString()));
        }
        return certEntityRepository.getRevocation(certEntity);
    }

    /**
     * This method adds certificate revocation information to {@code BasicOCSPRespBuilder}
     *
     * @param builder {@link BasicOCSPRespBuilder} to enrich with revocation status information
     * @param ocspReq {@link OCSPReq} containing the generated OCSP request
     * @param certEntityRevocation {@link CertEntityRevocation} containing revocation status information about the certificate
     */
    protected void addRevocationStatusToOCSPResponse(BasicOCSPRespBuilder builder, OCSPReq ocspReq,
                                                     CertEntityRevocation certEntityRevocation) {
        Objects.requireNonNull(ocspReq, "OCSPReq cannot be null!");
        if (Utils.isArrayEmpty(ocspReq.getRequestList())) {
            throw new IllegalStateException("OCSPReq list cannot be empty!");
        }

        Req r = ocspReq.getRequestList()[0];
        if (certEntityRevocation == null || certEntityRevocation.getRevocationDate() == null) {
            builder.addResponse(r.getCertID(), CertificateStatus.GOOD, getThisUpdate(), getNextUpdate());
        } else {
            RevokedStatus status = new RevokedStatus(certEntityRevocation.getRevocationDate(), certEntityRevocation.getRevocationReason().getValue());
            builder.addResponse(r.getCertID(), status, getThisUpdate(), getNextUpdate());
        }
    }

    /**
     * Returns a signature algorithm to be used on OCSP response creation
     *
     * @param ocspResponder {@link CertEntity} to sign the OCSP response
     * @return {@link SignatureAlgorithm}
     */
    protected SignatureAlgorithm getSignatureAlgorithm(CertEntity ocspResponder) {
        EncryptionAlgorithm currentEncryptionAlgorithm = this.encryptionAlgorithm;
        if (currentEncryptionAlgorithm != null) {
            if (!currentEncryptionAlgorithm.isEquivalent(ocspResponder.getEncryptionAlgorithm())) {
                throw new IllegalArgumentException(String.format(
                        "Defined EncryptionAlgorithm '%s' is not equivalent to the one returned by OCSP Issuer '%s'", currentEncryptionAlgorithm, ocspResponder.getEncryptionAlgorithm()));

            }
        } else {
            currentEncryptionAlgorithm = ocspResponder.getEncryptionAlgorithm();
        }
        return SignatureAlgorithm.getAlgorithm(currentEncryptionAlgorithm, digestAlgorithm);
    }

    /**
     * Initializes a {@code BasicOCSPRespBuilder}
     *
     * @param ocspResponderCertificate {@link CertificateToken} OCSP issuer certificate
     * @return {@link BasicOCSPRespBuilder}
     * @throws OperatorCreationException if an error on SKI computation occurs
     * @throws OCSPException if an error on RespId building occurs
     */
    protected BasicOCSPRespBuilder initBuilder(CertificateToken ocspResponderCertificate) throws OperatorCreationException, OCSPException {
        RespID respID = getRespID(ocspResponderCertificate);
        return new BasicOCSPRespBuilder(respID);
    }

    /**
     * Builds a {@code org.bouncycastle.cert.ocsp.RespID}
     *
     * @param ocspResponderCertificate {@link CertificateToken} OCSP issuer certificate
     * @return {@link RespID}
     * @throws OperatorCreationException if an error on SKI computation occurs
     * @throws OCSPException if an error on RespId building occurs
     */
    protected RespID getRespID(CertificateToken ocspResponderCertificate) throws OperatorCreationException, OCSPException {
        X509CertificateHolder x509CertificateHolder = DSSASN1Utils.getX509CertificateHolder(ocspResponderCertificate);
        if (responderIdByKey) {
            SubjectPublicKeyInfo info = x509CertificateHolder.getSubjectPublicKeyInfo();
            DigestCalculator digestCalculator = new BcDigestCalculatorProvider().get(
                    DSSASN1Utils.getAlgorithmIdentifier(DigestAlgorithm.SHA1));
            return new RespID(info, digestCalculator);
        } else {
            X500Name x500CertificateName = x509CertificateHolder.getSubject();
            return new RespID(x500CertificateName);
        }
    }

    /**
     * Builds an OCSP request for the given {@code CertificateToken}
     *
     * @param certificateToken {@link CertificateToken} to get OCSP request for
     * @param issuerCertificateToken {@link CertificateToken} issued the {@code certificateToken}
     * @return {@link OCSPReq}
     */
    protected OCSPReq buildOCSPRequest(CertificateToken certificateToken, CertificateToken issuerCertificateToken) {
        try {
            final OCSPReqBuilder ocspReqBuilder = new OCSPReqBuilder();

            final CertificateID certId = DSSRevocationUtils
                    .getOCSPCertificateID(certificateToken, issuerCertificateToken, digestAlgorithm);
            ocspReqBuilder.addRequest(certId);

            return ocspReqBuilder.build();

        } catch (OCSPException e) {
            throw new PKIException("Cannot build OCSP Request", e);
        }
    }

}