package eu.europa.esig.dss.jades.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.Map;

import org.jose4j.json.JsonUtil;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;

import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESTimestampParameters;
import eu.europa.esig.dss.jades.JAdESUtils;
import eu.europa.esig.dss.jades.JWSConstants;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.signature.DocumentSignatureService;
import eu.europa.esig.dss.utils.Utils;

public class JAdESLevelTFlattenedSerializationTest extends AbstractJAdESTestSignature {

	private DocumentSignatureService<JAdESSignatureParameters, JAdESTimestampParameters> service;
	private DSSDocument documentToSign;
	private JAdESSignatureParameters signatureParameters;

	@BeforeEach
	public void init() throws Exception {
		service = new JAdESService(getCompleteCertificateVerifier());
		service.setTspSource(getGoodTsa());

		documentToSign = new FileDocument(new File("src/test/resources/sample.json"));
		signatureParameters = new JAdESSignatureParameters();
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
		signatureParameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_T);
		
		signatureParameters.setJwsSerializationType(JWSSerializationType.FLATTENED_JSON_SERIALIZATION);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	protected void onDocumentSigned(byte[] byteArray) {
		super.onDocumentSigned(byteArray);
		
		assertTrue(JAdESUtils.isJsonDocument(new InMemoryDocument(byteArray)));
		try {
			Map<String, Object> rootStructure = JsonUtil.parseJson(new String(byteArray));
			
			String firstEntryName = rootStructure.keySet().iterator().next();
			assertEquals(JWSConstants.PAYLOAD, firstEntryName);
			
			String payload = (String) rootStructure.get(firstEntryName);
			assertNotNull(payload);
			assertTrue(Utils.isArrayNotEmpty(JAdESUtils.fromBase64Url(payload)));
			
			String header = (String) rootStructure.get(JWSConstants.PROTECTED);
			assertNotNull(header);
			assertTrue(Utils.isArrayNotEmpty(JAdESUtils.fromBase64Url(header)));
			
			String signatureValue = (String) rootStructure.get(JWSConstants.SIGNATURE);
			assertNotNull(signatureValue);
			assertTrue(Utils.isArrayNotEmpty(JAdESUtils.fromBase64Url(signatureValue)));
			
			Map<String, Object> unprotected = (Map<String, Object>) rootStructure.get(JWSConstants.HEADER);
			assertTrue(Utils.isMapNotEmpty(unprotected));

		} catch (JoseException e) {
			fail("Unable to parse the signed file : " + e.getMessage());
		}
		
	}

	@Override
	protected DSSDocument getDocumentToSign() {
		return documentToSign;
	}

	@Override
	protected DocumentSignatureService<JAdESSignatureParameters, JAdESTimestampParameters> getService() {
		return service;
	}

	@Override
	protected JAdESSignatureParameters getSignatureParameters() {
		return signatureParameters;
	}

	@Override
	protected String getSigningAlias() {
		return GOOD_USER;
	}

}
