package com.adobedemosystemprogram42.core.services.Impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.adobe.fd.assembler.service.AssemblerService;
import com.adobe.fd.output.api.OutputService;
import com.adobedemosystemprogram42.core.services.AEMFormsHelper;

@Component(
	    immediate = true,
	    service = AEMFormsHelper.class)
public class AEMFormsHelperImpl implements AEMFormsHelper {
	
	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Reference
	AssemblerService assemblerService;

	@Reference
	OutputService outputService;
	
	protected final Logger log = LoggerFactory.getLogger(getClass());

	//ConvertPdfService convertPDFService;

	public org.w3c.dom.Document getOrgW3cDocument(String repositoryPath) {
		ResourceResolver resolver = null;
		try {
			resolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
		} catch (org.apache.sling.api.resource.LoginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Resource res = resolver.getResource(repositoryPath);
		Node xmlDataNode = (Node) res.adaptTo(Node.class);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = null;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		InputStream xmlStream = null;
		try {
			xmlStream = xmlDataNode.getProperty("jcr:data").getBinary().getStream();
		} catch (ValueFormatException e) {
			e.printStackTrace();
		} catch (PathNotFoundException e) {
			e.printStackTrace();
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		org.w3c.dom.Document XmlDocument = null;
		try {
			XmlDocument = dBuilder.parse(xmlStream);
			log.info("Got xml document");
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return XmlDocument;
	}

	public com.adobe.aemfd.docmanager.Document getAEMFDDocument(String repositoryPath) {
		try {
			ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
		
			Resource res = resolver.getResource(repositoryPath);
			Node xmlDataNode = (Node) res.adaptTo(Node.class);
			InputStream xmlDataStream = xmlDataNode.getProperty("jcr:data").getBinary().getStream();
			com.adobe.aemfd.docmanager.Document xmlDocument = new com.adobe.aemfd.docmanager.Document(xmlDataStream);
			log.info("The path of the resource is " + res.getPath());
			log.info("the size of document " + xmlDocument.length());
			return xmlDocument;
		} catch (ValueFormatException e) {
			e.printStackTrace();
		} catch (PathNotFoundException e) {
			e.printStackTrace();
		} catch (RepositoryException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}catch (org.apache.sling.api.resource.LoginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public com.adobe.aemfd.docmanager.Document orgW3CDocumentToAEMFDDocument(org.w3c.dom.Document xmlDocument) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		DOMSource source = new DOMSource(xmlDocument);
		log.info("$$$$In orgW3CDocumentToAEMFDDocument method");
		StreamResult outputTarget = new StreamResult(outputStream);
		try {
			TransformerFactory.newInstance().newTransformer().transform(source, outputTarget);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			e.printStackTrace();
		}
		InputStream is1 = new ByteArrayInputStream(outputStream.toByteArray());
		com.adobe.aemfd.docmanager.Document xmlAEMFDDocument = new com.adobe.aemfd.docmanager.Document(is1);
		return xmlAEMFDDocument;
	}

	/*public com.adobe.aemfd.docmanager.Document flattenDocument(InputStream pdf_or_xdp, InputStream xmlDocument) {
		log.info("In flatten document");
		com.adobe.aemfd.docmanager.Document pdf_or_xdp_document = new com.adobe.aemfd.docmanager.Document(pdf_or_xdp);
		log.info("got pdf_xdp_document");
		com.adobe.aemfd.docmanager.Document xmlDataDocument = new com.adobe.aemfd.docmanager.Document(xmlDocument);
		log.info("got xml document");
		PDFOutputOptions pdfOptions = new PDFOutputOptions();
		pdfOptions.setAcrobatVersion(AcrobatVersion.Acrobat_11);
		try {
			//OutputService outputService=AEMUtils.getServiceReference(OutputService.class);
			com.adobe.aemfd.docmanager.Document generatedDocument = outputService
					.generatePDFOutput(pdf_or_xdp_document, xmlDataDocument, pdfOptions);
			log.info("did I get the document?" + generatedDocument.length());
			return generatedDocument;
		} catch (OutputServiceException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public com.adobe.aemfd.docmanager.Document assembleDocuments(List listOfPaths,
			com.adobe.aemfd.docmanager.Document ddxDocument) {
		log.info("I am in assemble document");

		ResourceResolver resolver = null;
		try {
			resolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
		} catch (org.apache.sling.api.resource.LoginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		AssemblerOptionSpec aoSpec = new AssemblerOptionSpec();
		aoSpec.setFailOnError(true);
		Map<String, Object> mapOfDocuments = new HashMap();
		try {
			for (int i = 0; i < listOfPaths.size(); i++) {
				Resource res = resolver.getResource((String) listOfPaths.get(i));
				Node documentNode = (Node) res.adaptTo(Node.class);
				log.info("####In bundle" + documentNode.getPath());
				InputStream documentDataStream = null;
				documentDataStream = documentNode.getProperty("jcr:data").getBinary().getStream();
				com.adobe.aemfd.docmanager.Document doc = new com.adobe.aemfd.docmanager.Document(documentDataStream);
				log.info("created doc " + i);
				mapOfDocuments.put(String.valueOf(i), doc);
				doc = null;
			}
			
			//AssemblerService assemblerService=AEMUtils.getService(AssemblerServiceImpl.class, AssemblerService.class);
			//AssemblerResult ar = assemblerService.invoke(ddxDocument, mapOfDocuments, aoSpec);
			//return (com.adobe.aemfd.docmanager.Document) ar.getDocuments().get("GeneratedDocument.pdf");

			AssemblerResult ar = assemblerService.invoke(ddxDocument, mapOfDocuments, aoSpec);
			return (com.adobe.aemfd.docmanager.Document) ar.getDocuments().get("GeneratedDocument.pdf");
		} catch (LoginException e) {
			e.printStackTrace();
		} catch (ValueFormatException e) {
			e.printStackTrace();
		} catch (PathNotFoundException e) {
			e.printStackTrace();
		} catch (RepositoryException e) {
			e.printStackTrace();
		} catch (com.adobe.fd.assembler.client.OperationException e) {
			e.printStackTrace();
		} 
		return null;
	}

	

	public com.adobe.aemfd.docmanager.Document simpleassemblyOfDocuments(List<String> listOfPaths) {
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			org.w3c.dom.Document ddx = docBuilder.newDocument();
			Element rootElement = ddx.createElementNS("http://ns.adobe.com/DDX/1.0/", "DDX");

			ddx.appendChild(rootElement);
			Element pdfResult = ddx.createElement("PDF");
			pdfResult.setAttribute("result", "GeneratedDocument.pdf");
			rootElement.appendChild(pdfResult);
			for (int i = 0; i < listOfPaths.size(); i++) {
				Element pdfSourceElement = ddx.createElement("PDF");
				pdfSourceElement.setAttribute("source", String.valueOf(listOfPaths.size() - 1));
				pdfResult.appendChild(pdfSourceElement);
				log.info("Appended to pdf result");
			}
			return assembleDocuments(listOfPaths, orgW3CDocumentToAEMFDDocument(ddx));
		} catch (ParserConfigurationException e) {
		}
		return null;
	}

	

	public List<com.adobe.aemfd.docmanager.Document> exportPDF(com.adobe.aemfd.docmanager.Document pdfDocument) {
		ToImageOptionsSpec spec = new ToImageOptionsSpec();

		log.info("$$$$$$$Successfully set  the image spec");
		spec.setImageConvertFormat(ImageConvertFormat.TIFF);
		spec.setGrayScaleCompression(GrayScaleCompression.Low);
		spec.setColorCompression(ColorCompression.LZW);
		spec.setFormat(JPEGFormat.BaselineOptimized);
		spec.setRgbPolicy(RGBPolicy.Off);
		spec.setCmykPolicy(CMYKPolicy.Off);
		//spec.setColorSpace(ColorSpace.TYPE_RGB);
		spec.setResolution("200");
		spec.setMonochrome(MonochromeCompression.None);
		spec.setFilter(PNGFilter.Sub);

		spec.setInterlace(Interlace.Adam7);
		spec.setTileSize(180);
		spec.setGrayScalePolicy(GrayScalePolicy.Off);
		try {
			List<com.adobe.aemfd.docmanager.Document> tiffs = this.convertPDFService.toImage(pdfDocument, spec);
			log.info("The size of the tiffs is " + tiffs.size());
			return tiffs;
		} catch (ConvertPdfException e) {
			e.printStackTrace();
		}
		return null;
	} */


}
