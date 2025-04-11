
package com.adobedemosystemprogram42.core.servlets;

import java.io.IOException;
import java.util.Map;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobedemosystemprogram42.core.services.AssetService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Component(service = {Servlet.class})
@SlingServletResourceTypes(
        resourceTypes = "cps/components/orderedAssetList",
        methods = HttpConstants.METHOD_GET
)
public class GetOrderedAssetList extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    AssetService assetService;

    @Override
    protected void doGet(final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws IOException {

        log.info("***-- GetOrderedAssetList Servlet started --***");
       
        JsonArray responseJson = new JsonArray();
        String path = request.getParameter("path");
        if (path != null){
            Map<String,String> orderedAssets = assetService.getOrderedAssets(path);
            for (Map.Entry<String, String> entry : orderedAssets.entrySet()) {
                JsonObject jsonObj = new JsonObject();
                jsonObj.addProperty("title", entry.getKey());
                jsonObj.addProperty("path",entry.getValue());
                responseJson.add(jsonObj);
            }
       }
        response.setContentType("application/json");
        response.getWriter().write(responseJson.toString());
    }
}
