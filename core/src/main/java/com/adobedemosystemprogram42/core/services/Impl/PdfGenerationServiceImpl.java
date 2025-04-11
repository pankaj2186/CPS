package com.adobedemosystemprogram42.core.services.Impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobedemosystemprogram42.core.services.PdfGenerationService;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import com.day.cq.dam.api.Rendition;
import com.google.gson.Gson;
import com.google.gson.JsonObject;


@Component(immediate = true, service = PdfGenerationService.class)
public class PdfGenerationServiceImpl implements PdfGenerationService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    private static final String CLIENT_ID = "{{client-id}}";
    private static final String CLIENT_SECRET = "{client-secret}}";
    private static final String PDF_SERVICE_URL = "{{pdf-service-url}}";
    private static final String X_API_KEY = "{{client-id}}";

    private static String accessToken;
    private static String uploadLink;
    private static String assetId;
    private static String statusUrl;
    private static String downloadUrl;
    private static String fileName;

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    private static final Map<String, String> mimeTypes = new HashMap<>();
    static {
        // Documents
        mimeTypes.put("doc", "application/msword");
        mimeTypes.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypes.put("ppt", "application/vnd.ms-powerpoint");
        mimeTypes.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        mimeTypes.put("xls", "application/vnd.ms-excel");
        mimeTypes.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypes.put("rtf", "application/rtf");
        mimeTypes.put("txt", "text/plain");
        mimeTypes.put("html", "text/html");
        mimeTypes.put("csv", "text/csv");
        mimeTypes.put("xml", "application/xml");

        // Images
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("gif", "image/gif");
    }

    @Override
    public void convertNonPdfToPdf(String inputFilePath, String outputFilePath) {
        log.info("--------- Convert non PDf using PDF services -------");
        try{

            ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
            
            Session session = resolver.adaptTo(Session.class);
            Resource assetResource = resolver.resolve(inputFilePath);
            Asset asset = assetResource.adaptTo(Asset.class); 
            Rendition original = asset.getOriginal();
            if (original != null) { 
                    InputStream fileInputStream = original.getStream(); 

                    // read token from /var/cps node
                    accessToken = getTokenfromCrx(session);

                    // Make the API request with the bearer token
                    fileName = FilenameUtils.getName(inputFilePath);
                    HttpResponse<InputStream> response = null;

                    byte[] binaryFile = generatePdf(fileInputStream,session);
                    
                    if (binaryFile != null) {
                        ValueFactory valueFactory = session.getValueFactory();
                        Binary binary = valueFactory.createBinary(new ByteArrayInputStream(binaryFile));
                        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
                        assetManager.createOrUpdateAsset(outputFilePath, binary, "application/pdf", true);
                    }
                    session.save();
                    
                    if (resolver.hasChanges()) {
                        resolver.commit();
                        resolver.refresh();
                    }
                }

                // TODO Notification to admin groups.
            
            } catch( Exception e){
                log.error(" Error while generating PDF :: "+ e.getMessage());
                e.printStackTrace();
            }     
    }

    /**
     * 
     * 
     * @param fileInputStream
     * @return
     * @throws Exception
     */
    private byte[] generatePdf(InputStream fileInputStream, Session session) throws Exception {
        int count = 0;
        if (accessToken == null || accessToken.isEmpty()){
            getAccessToken(session);
        }
        boolean staleToken = getUploadUrl();
        if (staleToken && count == 0){
            count ++;
            log.info("Oauth token is not valid. Generating token and calling getUploadUrl once again! ");
            getAccessToken(session);
            getUploadUrl();
        }
        uploadDocument(fileInputStream); // Update this
        createPdf();
        checkStatusUntilDone();
        byte[] binaryFile = downloadPdf("output.pdf");
        return binaryFile;
    }

    /**
     * 
     * @param session 
     * @throws IOException
     * @throws InterruptedException
     */
    private void getAccessToken(Session session) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PDF_SERVICE_URL + "/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(
                "client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        accessToken = "bearer " + json.get("access_token").getAsString();
        saveToken( session);
        log.info("Access Token: " + accessToken);
    }

    /**
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean getUploadUrl() throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        boolean staleToken = false;
        //body.addProperty("mediaType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        body.addProperty("mediaType", getMimeType(fileName));


        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PDF_SERVICE_URL + "/assets"))
            .header("Content-Type", "application/json")
            .header("X-API-Key", X_API_KEY)
            .header("Authorization", accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        if (response.statusCode() == 200) {
            uploadLink = json.get("uploadUri").getAsString();
            assetId = json.get("assetID").getAsString();
            log.info("Upload Link: " + uploadLink);
            log.info("Asset ID: " + assetId);
        } else if (response.statusCode() == 401){
            staleToken = true;
        } else {
            log.error("getUploadUrl not successful. Upload link not generated. ");
        }  
        return staleToken;     
    }

    /**
     * 
     * @param fis
     * @throws IOException
     * @throws InterruptedException
     */
    private void uploadDocument(InputStream fis) throws IOException, InterruptedException {
        byte[] fileBytes = IOUtils.toByteArray(fis);
        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uploadLink))
        .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
        //.header("Content-Length", String.valueOf(fileBytes.length))
        //.header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        .header("Content-Type", getMimeType(fileName))
        .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Document uploaded successfully. Status: " + response.statusCode());
        } else {
            log.error("Failed to upload document. Status: " + response.statusCode());
        }
    }

    /**
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    private void createPdf() throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("assetID", assetId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PDF_SERVICE_URL + "/operation/createpdf"))
            .header("Authorization", accessToken)
            .header("x-api-key", X_API_KEY)
            .header("Content-Type", "application/json")
            .header("Content-Disposition", "inline; filename=\"request.json\"")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            statusUrl = response.headers().firstValue("location").orElseThrow();
            log.info("PDF creation initiated. Status URL: " + statusUrl);
        } else {
            log.info("createPdf call failed. Status URL not generated. " + statusUrl);
        }
    }

    /**
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    private void checkStatusUntilDone() throws IOException, InterruptedException {
        while (true) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(statusUrl))
                .header("Authorization", accessToken)
                .header("x-api-key", X_API_KEY)
                .header("Content-Type", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            String status = json.get("status").getAsString();
            log.info("PDF status: " + status);

            if ("done".equalsIgnoreCase(status)) {
                downloadUrl = json.getAsJsonObject("asset").get("downloadUri").getAsString();
                break;
            }
            Thread.sleep(2000); // Wait before retry
        }
    }

    /**
     * 
     * @param outputPath
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private byte[] downloadPdf(String outputPath) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .GET()
            .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        log.info("PDF downloaded to: " + outputPath);

        byte[] responseBody = response.body();
        
        if (response.statusCode() == 200 && responseBody != null){
            log.info("PDF Download call succesful ");
            return responseBody;
        } else {
            log.error("PDF Download call failed ");
        }
        return responseBody;
   }

    /**
     * 
     * @param token
     * @param session
     */
    private void saveToken(Session session) {
        try {

            if (session != null) {
                Node contentNode = session.getNode("/content");
                Node cpsNode = contentNode.hasNode("cps") ? contentNode.getNode("cps") : contentNode.addNode("cps");

                // Create a property to save the token
                cpsNode.setProperty("token", accessToken);

                // Save the changes to the repository
                session.save();
                
            }
        } catch (RepositoryException e) {
            log.error("Error while saving token "+ e.getMessage());
        }
    }

    /**
     * 
     * @param session
     * @return
     */
    private String getTokenfromCrx(Session session) {
        String token = null;
        if (session != null) {
            try {
                // Path to the node you want to read
                String nodePath = "/content/cps";

                // Get the node from the JCR repository
                Node node = session.getNode(nodePath);

                // Retrieve a specific property from the node (e.g., 'propertyName')
                if (node.hasProperty("token")) {
                    Property property = node.getProperty("token");
                    token = property.getString(); // Get the value as a String
                    
                } 

            } catch (RepositoryException e) {
                log.error("Error while fetching token "+ e.getMessage());
            }
        }

        return token;
    }

    /**
     * 
     * @param filename
     * @return
     */
    public static String getMimeType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "application/octet-stream"; // Default binary type
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return mimeTypes.getOrDefault(extension, "application/octet-stream");
    }
   
}
