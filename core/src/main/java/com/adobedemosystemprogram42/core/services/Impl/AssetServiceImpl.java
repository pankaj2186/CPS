package com.adobedemosystemprogram42.core.services.Impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobedemosystemprogram42.core.services.AssetService;

@Component(immediate = true, service = AssetService.class)
public class AssetServiceImpl implements AssetService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    @Override
    public Map<String, String> getOrderedAssets(String path) {
        
        Map<String, String> map = new LinkedHashMap<>();
        
        try {
            ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
            Session session = resolver.adaptTo(Session.class);
            if (path != null) {
                Node node = session.getNode(path);
                NodeIterator nodes = node.getNodes();
                while (nodes.hasNext()) {
                    Node assteNode = nodes.nextNode();
                    if("dam:Asset".equals(assteNode.getPrimaryNodeType().toString())) {
                        map.put(assteNode.getName(), assteNode.getPath());
                    }
                }
            }
            session.save();
            if(resolver.hasChanges()) {
                resolver.commit();
                resolver.refresh();
            }
            
        } catch (LoginException | PersistenceException | RepositoryException e){
            log.error("Error while getting odered asset list ::  ", e);
        }

        return map;
    }

    @Override
    public void creatFolder(String folderName, String path) {
        try {
            ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "read-write-service"));
            Session session = resolver.adaptTo(Session.class);

            // Create the folder if it doesn't exist
            if (!session.nodeExists(path + "/" + folderName)) {
                Node parentNode = session.getNode(path);
                Node folderNode = parentNode.addNode(folderName, "sling:OrderedFolder");
                folderNode.setProperty("jcr:title", folderName +" WIP");
                session.save();
            } else {
                Node parentNode = session.getNode(path + "/" + folderName);
                NodeIterator nodes = parentNode.getNodes();
                while (nodes.hasNext()) {
                    Node assteNode = nodes.nextNode();
                    Resource res = resolver.resolve(assteNode.getPath());
                    log.info("--- Deleteing asset from WIP foler : " + res.getPath());
                    resolver.delete(res);
                }
            }

            if (resolver.hasChanges()) {
                resolver.commit();
                resolver.refresh();
            }

        } catch (LoginException | PersistenceException | RepositoryException e){
            log.error("Error while creating folder ::  ", e);
        }
    }

    
    
}
