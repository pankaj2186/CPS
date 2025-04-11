package com.adobedemosystemprogram42.core.services.Impl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobedemosystemprogram42.core.services.PdfGenerationService;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itextpdf.text.Document;


@Component(immediate = true, service = PdfGenerationService.class)
public class PdfGenerationLogicApiServiceImpl implements PdfGenerationService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    @Override
    public void convertNonPdfToPdf(String inputFilePath, String outputFilePath) {
        log.info("--------- Convert non PDf using Logic API-------");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Document pdfDocument = new Document();
        try{

            ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
            
            Session session = resolver.adaptTo(Session.class);
            Resource assetResource = resolver.resolve(inputFilePath);
            Asset asset = assetResource.adaptTo(Asset.class); 
            Rendition original = asset.getOriginal();
            if (original != null) { 
                    InputStream fileInputStream = original.getStream(); 

                    // read token from /var/cps node
                    String token = getBearerToken(session);

                    // Make the API request with the bearer token
                    String fileName = FilenameUtils.getBaseName(inputFilePath);
                    HttpResponse<InputStream> response = null;
                    
                    if(token != null && !token.isEmpty()){
                        response = makeApiRequest(fileInputStream, token, fileName);
                    }
                    
                    // Check if we received a 502 Bad Gateway error
                    if (token == null || response.statusCode() == HttpURLConnection.HTTP_BAD_GATEWAY) {
                        // Regenerate the bearer token
                        token = generateBearerToken();

                        // save latest token
                        saveToken(token,session);

                        pdfConvert(fileInputStream, token);
                        
                        // Retry the API request with the new token
                        response = makeApiRequest(fileInputStream, token,fileName);
                    }

                    // Handle the response
                    int responseCode = response.statusCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()));
                        String line;
                        StringBuilder stringBuilder = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                        reader.close();
                        log.info("Response: " + stringBuilder.toString());
                    } else {
                        log.error("Error: " + responseCode);
                    }
                    
                    /*// Upload file to dam
                    ValueFactory valueFactory = session.getValueFactory();
                    Binary binary = valueFactory.createBinary(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
                    AssetManager assetManager = resolver.adaptTo(AssetManager.class);
                    assetManager.createOrUpdateAsset(outputFilePath, binary, "application/pdf", true);
                    */
                    session.save();
                    if(resolver.hasChanges()) {
                        resolver.commit();
                        resolver.refresh();
                    }
                }
            
            } catch( LoginException | IOException  | RepositoryException | InterruptedException e){
                log.error(" Error !!!! "+ e.getMessage());
            }     
    }

    
    private HttpResponse<InputStream> makeApiRequest(InputStream fileInputStream, String token,String fileName) throws IOException, InterruptedException {
        String url = "{{logic-api-url}}";
        byte[] fileData = IOUtils.toByteArray(fileInputStream);
        HttpClient client = HttpClient.newHttpClient();
        String boundary = "--Boundary--";
        String contentType = "multipart/form-data; boundary=" + boundary;
        // Prepare request body
       /*  String body = "--" + boundary + "\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
        "Content-Type: application/octet-stream\r\n" +
        "\r\n" + new String(fileData) + "\r\n" +
        "--" + boundary + "--";*/

        StringBuilder body = new StringBuilder();
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        body.append("Content-Type: application/octet-stream\r\n\r\n");
        body.append(new String(fileData));//.append("\r\n");
        body.append("--").append(boundary).append("--").append("\r\n");

        // Prepare the request
        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .setHeader("token", token)
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();

        // Send the request and handle response
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return response;
}

    private String generateBearerToken() throws IOException {
        // Define the URL for the API endpoint
        URL url = new URL("{{token-url}}");
        
        // Open the connection to the URL
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // Set the request method
        connection.setRequestMethod("POST");
        
        // Set the necessary headers
        //connection.setRequestProperty("Cookie", "relay=bdc45bc0-d49c-4620-8b6c-3994e6f91687; ftrset=133");
        connection.setDoOutput(true);
        
        // Prepare the form data
        String data = "code={{code}}&" +
                      "client_id={{client_id}}&" +
                      "client_secret={{client_secret}}&" +
                      "grant_type=authorization_code";
        
        // Write the form data to the connection's output stream
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = data.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Get the response code
        int responseCode = connection.getResponseCode();
        System.out.println("Response Code: " + responseCode);
        StringBuilder response = new StringBuilder();
        // Read the response from the input stream
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            // Print the response
            System.out.println("Response: " + response.toString());
            // Close the connection
             connection.disconnect();
            
        }
        connection.disconnect();
        
        JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
        String accessToken = jsonObject.get("access_token").getAsString();
        return accessToken;
        
    }

    private void saveToken(String token, Session session) {
        try {

            if (session != null) {
                Node rootNode = session.getRootNode();
                Node varNode = rootNode.hasNode("var") ? rootNode.getNode("var") : rootNode.addNode("var");
                Node cpsNode = varNode.hasNode("cps") ? varNode.getNode("cps") : varNode.addNode("cps");

                // Create a property to save the token
                cpsNode.setProperty("token", token);

                // Save the changes to the repository
                session.save();
                
            }
        } catch (RepositoryException e) {
            log.error("Error while saving token "+ e.getMessage());
        }
    }

    private String getBearerToken(Session session) {
        String token = null;
        if (session != null) {
            try {
                // Path to the node you want to read
                String nodePath = "/var/cps";

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


    private void pdfConvert(InputStream fileInputStream, String token){

        String urlString = "{{logic-api-url}}";
    
        try {
            // Open a connection to the URL
            URL url = new URL(urlString);
            byte[] fileBytes = IOUtils.toByteArray(fileInputStream);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            // Set the request method to POST
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            
            // Set the headers (replace token with your actual token)
            //connection.setRequestProperty("Authorization", "Bearer "+token);
            connection.setRequestProperty("token", token);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
           

            // Create output stream for the body of the request
            try (OutputStream os = connection.getOutputStream()) {
                
                
                fileInputStream.read(fileBytes);
                os.write(fileBytes);
                fileInputStream.close();
            }

            // Get response code and response
            int responseCode = connection.getResponseCode();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Print the response from the API
            System.out.println("Response: " + response.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    



    
    
}
