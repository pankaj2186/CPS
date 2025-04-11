package com.adobedemosystemprogram42.core.workflow;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobedemosystemprogram42.core.services.AssetService;
import com.adobedemosystemprogram42.core.services.PdfGenerationService;

@Component(service = WorkflowProcess.class, property = {
        "process.label = CPS PDF Generation Process"
})
public class GeneratePDFProcess implements WorkflowProcess {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    ResourceResolverFactory resolverFactory;

    @Reference
    AssetService assetService;

    @Reference(target = "(component.name=com.adobedemosystemprogram42.core.services.Impl.PdfGenerationServiceImpl)")
    PdfGenerationService pdfHttpService;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) {

        log.info("***-- CPS PDF Generation Process Started --***");
        String sourcePath = workItem.getWorkflowData().getPayload().toString();
        String destinationPath = metaDataMap.get("PROCESS_ARGS", String.class);
        if (sourcePath == null || destinationPath == null) {
            log.warn("Source or Destination path not specified. PDF Genaration failed relocation failed.");
            return;
        }

        String folderName = FilenameUtils.getBaseName(sourcePath);
        log.info("------ Creating folder " + folderName);
        assetService.creatFolder(folderName, destinationPath);

        try {
            ResourceResolver resolver = resolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
            AssetManager assetManager = resolver.adaptTo(AssetManager.class);
            
            String destinationFolder = destinationPath + "/" +folderName;
            log.info("------ finalDestination " + destinationFolder);

            Map<String,String> orderedAssets = assetService.getOrderedAssets(sourcePath);
            for (Map.Entry<String, String> entry : orderedAssets.entrySet()) {
                Resource res = resolver.resolve(entry.getValue());
                if ("dam:Asset".equalsIgnoreCase(res.getResourceType())) {
                    log.info("------ Converting to PDF :  " + res.getPath());
                    String fileExtension = FilenameUtils.getExtension(res.getPath());
                    String fileName = FilenameUtils.getBaseName(res.getPath());
                    String outputFilePath = destinationFolder + "/" +fileName + ".pdf";
                    log.info("------ outputFilePath " + outputFilePath);
                    if("pdf".equalsIgnoreCase(fileExtension)){
                        assetManager.copyAsset(res.getPath(),outputFilePath);
                    } else {
                        pdfHttpService.convertNonPdfToPdf(res.getPath(), outputFilePath);
                    }

               }
            }
            if(resolver.hasChanges()){
                resolver.commit();
                resolver.refresh();
            }
           
        } catch (LoginException | PersistenceException e) {
            log.error("Login Exception has occurred : " + e.getMessage());
        }
    }
}
