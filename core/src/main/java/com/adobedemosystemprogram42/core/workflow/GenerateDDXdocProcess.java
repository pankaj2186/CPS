package com.adobedemosystemprogram42.core.workflow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobedemosystemprogram42.core.services.AssetService;
import com.day.cq.dam.api.AssetManager;

@Component(service = WorkflowProcess.class, property = {
        "process.label = CPS Generate DDX Document"
})
public class GenerateDDXdocProcess implements WorkflowProcess {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    ResourceResolverFactory resolverFactory;

    @Reference
    AssetService assetService;

    private boolean includeTOC = false;
    private String bookmarkLevel = "1";
    private boolean addFooterWithPageNumbers = false;
    private String frontCoverPath = null;
    private String wipFolderPath = null;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) {
        log.info(" ---- CPS Generate DDX Document ------");

        String sourcePath = workItem.getWorkflowData().getPayload().toString();
        String arguments = metaDataMap.get("PROCESS_ARGS", String.class);
        if (sourcePath == null || arguments == null) {
            log.warn("Source or arguments not specified. DDX Document Generation failed.");
            return;
        }

        Map<String, String> argMap = buildArguments(metaDataMap);
        includeTOC = argMap.get("includeTOC") != null ? Boolean.parseBoolean(argMap.get("includeTOC")) : false;
        bookmarkLevel = argMap.get("bookmarkLevel") != null ? argMap.get("bookmarkLevel") : "1";
        addFooterWithPageNumbers = argMap.get("addFooterWithPageNumbers") != null ? Boolean.parseBoolean(argMap.get("addFooterWithPageNumbers")) :false;
        //frontCoverPath = argMap.get("frontCoverPath") != null ? argMap.get("frontCoverPath") : null;
        wipFolderPath = argMap.get("wipFolderPath") != null ? argMap.get("wipFolderPath") : null;
        


        try {
            
            ResourceResolver resolver = resolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
            Resource resource = resolver.getResource(sourcePath);
            if (resource != null) {
                String caseFolderName = FilenameUtils.getBaseName(resource.getPath());
                Map<String,String> orderedAssets = assetService.getOrderedAssets(resource.getPath());
                Map<String,String> orderedWipAssets = orderWIPAssets(wipFolderPath +"/"+ caseFolderName +"/",orderedAssets,resolver);
                // Set front cover path - set at metadata level.
                frontCoverPath = getFrontCoverPath(orderedAssets, resolver);
                if (frontCoverPath != null) {
                    String frontCoverExtension = FilenameUtils.getExtension(frontCoverPath);
                    if (!"pdf".equalsIgnoreCase(frontCoverExtension)){
                        String frontCoverBasename = FilenameUtils.getBaseName(frontCoverPath);
                        frontCoverPath = wipFolderPath +"/"+ caseFolderName +"/"+ frontCoverBasename + ".pdf";
                    }
                }
                
                // Generate DDX doc
                Document doc = generateDdxDocument(resource, caseFolderName, orderedWipAssets,resolver);
                
                // Upload DDx doc
                String ddxFilePath = wipFolderPath +"/"+ caseFolderName +"/" + caseFolderName + ".ddx" ; 
                uploadDdxFile(ddxFilePath, doc,resolver);

            }
            
            if (resolver.hasChanges()){
                resolver.commit();
                resolver.refresh();
            }
            
        } catch (LoginException | IOException | TransformerException | RepositoryException e) {
            log.error("Exception has occurred." + e.getMessage());
        }
    }

     /**
     * Generate DDX document.
     * 
     * @param resource
     * @param resolver
     * @param wipFolderPath 
    * @return 
    */
    private Document generateDdxDocument(Resource resource, String caseFolderName, Map<String,String> orderedAssets,ResourceResolver resolver) {
        try {
            
            log.info("-------- generateDdxDocument() method start ------- ");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element root = (Element)doc.createElement("DDX");
            root.setAttribute("xmlns","http://ns.adobe.com/DDX/1.0/"); 
            doc.appendChild(root);


            // Create and append child elements
            Element resultPdf = doc.createElement("PDF");
            resultPdf.setAttribute("result", caseFolderName +"-bundle.pdf");
            root.appendChild(resultPdf);

            // Footer with page numbers
            if (addFooterWithPageNumbers) {
                Element footer = (Element)doc.createElement("Footer");
                footer.setAttribute("whiteout", "true");
                Element center = (Element)doc.createElement("center");
                footer.appendChild(center);
                Element styledText = (Element)doc.createElement("StyledText");
                center.appendChild(styledText);
                Element pElem = (Element)doc.createElement("p");
                Element pageNumber = (Element)doc.createElement("_PageNumber");
                pElem.appendChild(pageNumber);
                styledText.appendChild(pElem);
                resultPdf.appendChild(footer);
            }
             
            // Add front Cover
            if (null != frontCoverPath && !"".equalsIgnoreCase(frontCoverPath)){
                Element coverPdf = doc.createElement("PDF");
                Resource coverPdfResource = resolver.getResource(frontCoverPath);
                if (coverPdfResource != null) {
                    log.info(" FrontCover resource added to DDX - "+ coverPdfResource.getPath());
                    String fileName = FilenameUtils.getName(frontCoverPath);
                    coverPdf.setAttribute("source", fileName);
                    resultPdf.appendChild(coverPdf);
                }
            }


            // Table Of Content element
            if (includeTOC) {
                Element tableOfContents = (Element)doc.createElement("TableOfContents");
                tableOfContents.setAttribute("maxBookmarkLevel", bookmarkLevel);
                tableOfContents.setAttribute("createLiveLinks", "true");
                tableOfContents.setAttribute("bookmarkTitle", "Table of Contents");
                tableOfContents.setAttribute("includeInTOC", "true");
                resultPdf.appendChild(tableOfContents);
            }

            // Add Source PDFs    
            for (Map.Entry<String, String> entry : orderedAssets.entrySet()) {
                Element sourcePdf = doc.createElement("PDF");
                String pdfPath = entry.getValue();
                Resource pdfResource = resolver.getResource(pdfPath);
                String fileName = FilenameUtils.getName(pdfPath);
                String baseName = FilenameUtils.getBaseName(pdfPath);
                if (frontCoverPath != null){
                    String frontCoverName = FilenameUtils.getBaseName(frontCoverPath);
                    if (baseName != null && baseName.equals(frontCoverName)) {
                        continue; // Skip adding front cover again
                    }
                }
                if (baseName != null && baseName.endsWith("-bundle")) {
                    continue; // Skip bundle document
                }

                if (pdfResource != null) {

                String fileBaseName = FilenameUtils.getBaseName(pdfPath);
                sourcePdf.setAttribute("source", fileName);
                sourcePdf.setAttribute("bookmarkTitle", fileBaseName);
                resultPdf.appendChild(sourcePdf);
                }
            }
           return doc;
        } catch (ParserConfigurationException e) {
            log.error("Exception has occurred." + e.getMessage());
        }
        return null;
    }
          
    /**
     * Upload DDX document to provided DAM path.
     * 
     * @param doc
     * @param resolver
     * @throws TransformerException 
     * @throws RepositoryException 
     * @throws UnsupportedRepositoryOperationException 
     */
    private void uploadDdxFile(String ddxFilePath, Document doc, ResourceResolver resolver) throws TransformerException, UnsupportedRepositoryOperationException, RepositoryException {

        log.info("-------- uploadDdxFile() method start ------- :: "+ ddxFilePath);
        Session session = resolver.adaptTo(Session.class);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(outputStream);
        transformer.transform(source, result);

        ValueFactory valueFactory = session.getValueFactory();
        Binary binary = valueFactory.createBinary(new ByteArrayInputStream(outputStream.toByteArray()));
        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        assetManager.createOrUpdateAsset(ddxFilePath, binary, "text/ddx", true);
        log.info("-------- upload Complete :: "+ ddxFilePath);
        session.save();
    }

    /**
     * Get ordered asset from  WIP folder based on original case folder rorder.
     * 
     * @param wipFolderPath
     * @param orderedAssets
     * @param resolver
     * @return
     */
    private Map<String, String> orderWIPAssets(String wipFolderPath, Map<String, String> orderedAssets, ResourceResolver resolver) {
        Map<String, String> orderedWIPAssetMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : orderedAssets.entrySet()) {
            String filePath = entry.getValue();
            String fileName = FilenameUtils.getBaseName(filePath) +".pdf";
            Resource wipPdfResource = resolver.getResource(wipFolderPath + fileName);
            if (wipPdfResource != null){
                orderedWIPAssetMap.put(entry.getKey(), wipPdfResource.getPath());
            }
       }
       return orderedWIPAssetMap;
    }

    /**
     * Get front Cover path. Front cover is set at metadata level.
     * 
     * @param orderedAssets
     * @param resolver
     * @return
     */
    private String getFrontCoverPath(Map<String, String> orderedAssets, ResourceResolver resolver) {
       String frontCoverPath = null;
        for (Map.Entry<String, String> entry : orderedAssets.entrySet()) {
            Resource resource = resolver.getResource(entry.getValue());
            Resource metadataResource = resource.getChild("jcr:content/metadata");
            ValueMap properties = ResourceUtil.getValueMap(metadataResource);
            String frontCover = properties.get("frontCover", String.class);

            boolean isFrontCover = frontCover != null && !frontCover.isEmpty() ? Boolean.valueOf(frontCover) : false;
            if (isFrontCover){
                log.info("--- front cover :: "+ entry.getValue());
                return entry.getValue();
            }
       }
       return frontCoverPath;
    }

    Map<String, String> buildArguments(MetaDataMap metaDataMap){
       
        if (metaDataMap.containsKey("PROCESS_ARGS")) {
             String argumentsString = metaDataMap.get("PROCESS_ARGS", "string");
             String[] arguments = StringUtils.split(argumentsString, ",");
             return Arrays.stream(arguments)
                 .map(String::trim)
                 .map(s -> s.split("="))
                 .filter(s -> s.length > 1)
                 .collect(Collectors.toMap(s -> s[0], s -> s[1]));
         }
         return Collections.emptyMap();
    }
 
}
