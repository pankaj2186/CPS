package com.adobedemosystemprogram42.core.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FilenameUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.adobe.fd.assembler.client.AssemblerOptionSpec;
import com.adobe.fd.assembler.client.AssemblerResult;
import com.adobe.fd.assembler.service.AssemblerService;
import com.adobe.granite.taskmanagement.Task;
import com.adobe.granite.taskmanagement.TaskManager;
import com.adobe.granite.taskmanagement.TaskManagerException;
import com.adobe.granite.taskmanagement.TaskManagerFactory;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobedemosystemprogram42.core.services.AEMFormsHelper;
import com.adobedemosystemprogram42.core.services.AssetService;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;


@Component(service = WorkflowProcess.class, property = {
        "process.label = CPS Generate Bundle PDF"
})
public class GenerateBundlePDF implements WorkflowProcess {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    ResourceResolverFactory resolverFactory;

    @Reference
    AssetService assetService;

    @Reference
    AEMFormsHelper aemFormsHelper;

    @Reference
    AssemblerService assemblerService;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) {
        log.info("------ CPS Generate Bundle PDF Process Start ------- ");
        String sourcePath = workItem.getWorkflowData().getPayload().toString();
        String wipFolderPath = metaDataMap.get("PROCESS_ARGS", String.class);
        if (sourcePath == null || wipFolderPath == null) {
            log.error("Source or Destination path not specified. Bundle PDF Document Generation failed.");
            return;
        }
        try {
            
            ResourceResolver resolver = resolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
            Resource resource = resolver.getResource(sourcePath);
            if (resource != null) {
                String caseFolderName = FilenameUtils.getBaseName(resource.getPath());
                Map<String,String> orderedWipAssets = orderWIPAssets(wipFolderPath +"/"+ caseFolderName +"/",assetService.getOrderedAssets(resource.getPath()),resolver);

                // Read DDX document
                String ddxFilePath = wipFolderPath +"/"+ caseFolderName +"/" + caseFolderName + ".ddx" ; 
                Resource ddxResource = resolver.getResource(ddxFilePath);
                Asset asset = ddxResource.adaptTo(Asset.class);
                Resource original = asset.getOriginal();
                InputStream inputStream = original.adaptTo(InputStream.class);
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document ddxDoc =  dBuilder.parse(inputStream);

                // Generate Bundle PDF
                if (ddxResource != null) {
                    String bundleDocPath = resource.getPath()+ "/"+ caseFolderName +"-bundle.pdf";
                    com.adobe.aemfd.docmanager.Document bundledDoc = generateBundlePdf(resource,resolver,ddxDoc,orderedWipAssets,caseFolderName +"-bundle.pdf");
                    
                    // Upload Bundle PDF
                    if (bundledDoc != null) {
                        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
                        Session session = resolver.adaptTo(Session.class);
                        ValueFactory valueFactory = session.getValueFactory();
                        Binary binary = valueFactory.createBinary(bundledDoc.getInputStream());
                        assetManager.createOrReplaceAsset(bundleDocPath, binary, "application/pdf", true);
                        
                        // Delete WIP folder once bundle generation is successful.
                        Node node = session.getNode(wipFolderPath +"/"+ caseFolderName);
                        if (node != null) {
                            node.remove();
                        }
                        session.save();
                        notifyAdminGroup(resolver, sourcePath);
                    }
               }
            }
            
            if (resolver.hasChanges()){
                resolver.commit();
                resolver.refresh();
            }

        } catch (LoginException | IOException | RepositoryException | ParserConfigurationException | SAXException e) {
            log.error("Exception has occurred while generating bundle PDF." + e.getMessage());
        }
   }


    private void notifyAdminGroup(ResourceResolver resolver, String sourcePath) {
        TaskManager taskManager = resolver.adaptTo(TaskManager.class);
        TaskManagerFactory taskManagerFactory = taskManager.getTaskManagerFactory();
        
        try {
            Task newTask = taskManagerFactory.newTask("notification");
            newTask.setName("CPS Bundle Generation");
            newTask.setContentPath(sourcePath);
            newTask.setDescription("CPS Bundle created for "+sourcePath);
            newTask.setInstructions("CPS Bundle created for "+sourcePath);
            newTask.setCurrentAssignee("admin");
            taskManager.createTask(newTask);
        } catch (TaskManagerException e) {
            log.error("Could not sen notification to admin group ", e);
        }
    }

    /**
     * Generate map of Documents and pass to assembler Service.
     * 
     * @param resource
     * @param resolver
     * @param ddxDocument
     * @param orderedAssets
     * @param bundleDocName
     * @return
     */
    private com.adobe.aemfd.docmanager.Document generateBundlePdf(Resource resource, ResourceResolver resolver, Document ddxDocument, Map<String, String> orderedAssets, String bundleDocName) {
        log.info("-------- generateBundlePdf() method start ------- ");
        Map<String, Object> inputPdfMap = new HashMap();
        for (Map.Entry<String, String> entry : orderedAssets.entrySet()) {
            log.info("-------- generateBundlePdf for this path ::  "+entry.getValue());
            Resource pdfResource= resolver.getResource(entry.getValue());
            if (pdfResource != null) {
                log.info("-------- pdfResource ot null  "+pdfResource.getPath());
                Asset asset = pdfResource.adaptTo(Asset.class);
                Resource original = asset.getOriginal();
                InputStream inputStream = original.adaptTo(InputStream.class);
                com.adobe.aemfd.docmanager.Document pdfDoc = new com.adobe.aemfd.docmanager.Document(inputStream);
                inputPdfMap.put(pdfResource.getName(),pdfDoc);
           }
        }
        com.adobe.aemfd.docmanager.Document bundleDoc =  invokeDDX (ddxDocument, inputPdfMap, bundleDocName);
        return bundleDoc;
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
        Map<String, String> orderedWIPAssetMap = new HashMap();
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
     * Invoke assemble service to generate bundle PDF
     * 
     * @param ddx
     * @param mapOfDocuments
          * @param bundleDocPath 
          * @return
          */
         private com.adobe.aemfd.docmanager.Document invokeDDX(org.w3c.dom.Document ddx, Map<String, Object> mapOfDocuments, String bundleDocName) {
            log.info("-------- invokeDDX() method start ------- ");
            com.adobe.aemfd.docmanager.Document ddxDocument = aemFormsHelper.orgW3CDocumentToAEMFDDocument(ddx);
            AssemblerOptionSpec aoSpec = new AssemblerOptionSpec();
            aoSpec.setFailOnError(true);
            AssemblerResult ar = null;
            com.adobe.aemfd.docmanager.Document assembledDocument = null;
            try {
                ar = assemblerService.invoke(ddxDocument, mapOfDocuments, aoSpec);
                assembledDocument = (com.adobe.aemfd.docmanager.Document) ar.getDocuments().get(bundleDocName);
            }
            catch (Exception e) {
                log.error("Exception in writeDocument :",e);
            }
            return assembledDocument;
    }   
}